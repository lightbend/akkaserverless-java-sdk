/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kalix.springsdk.impl

import java.lang.reflect.Type

import scala.jdk.CollectionConverters.CollectionHasAsScala

import com.google.api.AnnotationsProto
import com.google.api.CustomHttpPattern
import com.google.api.HttpRule
import com.google.protobuf.ByteString
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto
import com.google.protobuf.DescriptorProtos.MethodOptions
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.Empty
import com.google.protobuf.{ Any => JavaPbAny }
import kalix.javasdk.impl.AnySupport
import kalix.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import kalix.springsdk.impl.reflection.AnyJsonRequestServiceMethod
import kalix.springsdk.impl.reflection.CombinedSubscriptionServiceMethod
import kalix.springsdk.impl.reflection.DeleteServiceMethod
import kalix.springsdk.impl.reflection.DynamicMessageContext
import kalix.springsdk.impl.reflection.ExtractorCreator
import kalix.springsdk.impl.reflection.KalixMethod
import kalix.springsdk.impl.reflection.NameGenerator
import kalix.springsdk.impl.reflection.ParameterExtractor
import kalix.springsdk.impl.reflection.ParameterExtractors
import kalix.springsdk.impl.reflection.ParameterExtractors.HeaderExtractor
import kalix.springsdk.impl.reflection.RestServiceIntrospector.BodyParameter
import kalix.springsdk.impl.reflection.RestServiceIntrospector.HeaderParameter
import kalix.springsdk.impl.reflection.RestServiceIntrospector.PathParameter
import kalix.springsdk.impl.reflection.RestServiceIntrospector.QueryParamParameter
import kalix.springsdk.impl.reflection.RestServiceIntrospector.UnhandledParameter
import kalix.springsdk.impl.reflection.ServiceMethod
import kalix.springsdk.impl.reflection.SubscriptionServiceMethod
import kalix.springsdk.impl.reflection.SyntheticRequestServiceMethod
import org.springframework.web.bind.annotation.RequestMethod

/**
 * The component descriptor is both used for generating the protobuf service descriptor to communicate the service type
 * and methods etc. to Kalix and for the reflective routers routing incoming calls to the right method of the user
 * component class.
 */
private[impl] object ComponentDescriptor {

  def descriptorFor(component: Class[_], messageCodec: SpringSdkMessageCodec): ComponentDescriptor =
    ComponentDescriptorFactory.getFactoryFor(component).buildDescriptorFor(component, messageCodec, new NameGenerator)

  def apply(
      nameGenerator: NameGenerator,
      messageCodec: SpringSdkMessageCodec,
      serviceName: String,
      serviceOptions: Option[kalix.ServiceOptions],
      packageName: String,
      kalixMethods: Seq[KalixMethod],
      additionalMessages: Seq[ProtoMessageDescriptors] = Nil): ComponentDescriptor = {

    val otherMessageProtos: Seq[DescriptorProtos.DescriptorProto] =
      additionalMessages.flatMap(pm => pm.mainMessageDescriptor +: pm.additionalMessageDescriptors)

    val grpcService = ServiceDescriptorProto.newBuilder()
    grpcService.setName(serviceName)

    serviceOptions.foreach { serviceOpts =>
      val options =
        DescriptorProtos.ServiceOptions
          .newBuilder()
          .setExtension(kalix.Annotations.service, serviceOpts)
          .build()
      grpcService.setOptions(options)
    }

    def methodToNamedComponentMethod(kalixMethod: KalixMethod): NamedComponentMethod = {

      kalixMethod.validate()

      val (inputMessageName: String, extractors: Map[Int, ExtractorCreator], inputProto: Option[DescriptorProto]) =
        kalixMethod.serviceMethod match {
          case serviceMethod: SyntheticRequestServiceMethod =>
            val (inputProto, extractors) =
              buildSyntheticMessageAndExtractors(nameGenerator, serviceMethod, kalixMethod.entityKeys)
            (inputProto.getName, extractors, Some(inputProto))

          case _: AnyJsonRequestServiceMethod =>
            (JavaPbAny.getDescriptor.getFullName, Map.empty[Int, ExtractorCreator], None)

          case _: DeleteServiceMethod =>
            (Empty.getDescriptor.getFullName, Map.empty[Int, ExtractorCreator], None)
        }

      val grpcMethodName = nameGenerator.getName(kalixMethod.serviceMethod.methodName.capitalize)
      val grpcMethodBuilder =
        buildGrpcMethod(
          grpcMethodName,
          inputMessageName,
          kalixMethod.serviceMethod.streamIn,
          kalixMethod.serviceMethod.streamOut)

      grpcMethodBuilder.setOptions(createMethodOptions(kalixMethod))

      val grpcMethod = grpcMethodBuilder.build()
      grpcService.addMethod(grpcMethod)

      NamedComponentMethod(
        kalixMethod.serviceMethod,
        messageCodec,
        grpcMethodName,
        extractors,
        inputMessageName,
        inputProto)
    }

    val namedMethods: Seq[NamedComponentMethod] = kalixMethods.map(methodToNamedComponentMethod)
    val inputMessageProtos: Set[DescriptorProtos.DescriptorProto] = namedMethods.flatMap(_.inputProto).toSet

    val fileDescriptor: Descriptors.FileDescriptor =
      ProtoDescriptorGenerator.genFileDescriptor(
        serviceName,
        packageName,
        grpcService.build(),
        inputMessageProtos ++ otherMessageProtos)

    val methods: Map[String, CommandHandler] =
      namedMethods.map { method => (method.grpcMethodName, method.toCommandHandler(fileDescriptor)) }.toMap

    val serviceDescriptor: Descriptors.ServiceDescriptor =
      fileDescriptor.findServiceByName(grpcService.getName)

    new ComponentDescriptor(serviceName, packageName, methods, serviceDescriptor, fileDescriptor)
  }

  private def createMethodOptions(kalixMethod: KalixMethod): MethodOptions = {

    val methodOptions = MethodOptions.newBuilder()

    kalixMethod.serviceMethod match {
      case syntheticRequestServiceMethod: SyntheticRequestServiceMethod =>
        val httpRuleBuilder = buildHttpRule(syntheticRequestServiceMethod)
        syntheticRequestServiceMethod.params.collectFirst { case BodyParameter(_, _) =>
          httpRuleBuilder.setBody("json_body")
        }
        methodOptions.setExtension(AnnotationsProto.http, httpRuleBuilder.build())
      case _ => //ignore
    }

    kalixMethod.methodOptions.foreach(option => methodOptions.setExtension(kalix.Annotations.method, option))
    methodOptions.build()
  }

  // intermediate format that references input message by name
  // once we have built the full file descriptor, we can look up for the input message using its name
  private case class NamedComponentMethod(
      serviceMethod: ServiceMethod,
      messageCodec: SpringSdkMessageCodec,
      grpcMethodName: String,
      extractorCreators: Map[Int, ExtractorCreator],
      inputMessageName: String,
      inputProto: Option[DescriptorProto]) {

    type ParameterExtractorsArray = Array[ParameterExtractor[InvocationContext, AnyRef]]

    def toCommandHandler(fileDescriptor: FileDescriptor): CommandHandler = {
      serviceMethod match {
        case method: SyntheticRequestServiceMethod =>
          val syntheticMessageDescriptor = fileDescriptor.findMessageTypeByName(inputMessageName)
          if (syntheticMessageDescriptor == null)
            throw new RuntimeException(
              "Unknown message type [" + inputMessageName + "], known are [" + fileDescriptor.getMessageTypes.asScala
                .map(_.getName) + "]")

          val parameterExtractors: ParameterExtractorsArray =
            if (method.callable) {
              method.params.zipWithIndex.map { case (param, idx) =>
                extractorCreators.find(_._1 == idx) match {
                  case Some((_, creator)) => creator(syntheticMessageDescriptor)
                  case None               =>
                    // Yet to resolve this parameter, resolve now
                    param match {
                      case hp: HeaderParameter =>
                        new HeaderExtractor[AnyRef](hp.name, identity)
                      case UnhandledParameter(p) =>
                        throw new RuntimeException(
                          s"Unhandled parameter for [${serviceMethod.methodName}]: [$p], message type: " + inputMessageName)
                      // FIXME not handled: BodyParameter(_, _), PathParameter(_, _), QueryParamParameter(_, _)
                    }
                }
              }.toArray
            } else Array.empty

          // synthetic request always have proto messages as input,
          // their type url will are prefixed by DefaultTypeUrlPrefix
          // It's possible for a user to configure another prefix, but this is done through the Kalix instance
          // and the Spring SDK doesn't expose it.
          val typeUrl = AnySupport.DefaultTypeUrlPrefix + "/" + syntheticMessageDescriptor.getFullName

          CommandHandler(
            grpcMethodName,
            messageCodec,
            syntheticMessageDescriptor,
            Map(typeUrl -> MethodInvoker(method.javaMethod, parameterExtractors)))

        case method: CombinedSubscriptionServiceMethod =>
          val methodInvokers =
            method.methodsMap.map { case (typeUrl, meth) =>
              val parameterExtractors: ParameterExtractorsArray =
                meth.getParameterTypes.map(param => ParameterExtractors.AnyBodyExtractor[AnyRef](param))

              (typeUrl, MethodInvoker(meth, parameterExtractors))
            }

          CommandHandler(grpcMethodName, messageCodec, JavaPbAny.getDescriptor, methodInvokers)

        case method: SubscriptionServiceMethod =>
          val methodInvokers =
            serviceMethod.javaMethodOpt.map { meth =>

              val parameterExtractors: ParameterExtractorsArray =
                Array(ParameterExtractors.AnyBodyExtractor(method.inputType))

              val typeUrl = messageCodec.typeUrlFor(method.inputType)
              (typeUrl, MethodInvoker(meth, parameterExtractors))
            }.toMap

          CommandHandler(grpcMethodName, messageCodec, JavaPbAny.getDescriptor, methodInvokers)

        case method: AnyJsonRequestServiceMethod =>
          val methodInvokers = serviceMethod.javaMethodOpt.map { meth =>

            val parameterExtractors: ParameterExtractorsArray =
              Array(ParameterExtractors.AnyBodyExtractor(method.inputType))

            val typeUrl = messageCodec.typeUrlFor(method.inputType)
            (typeUrl, MethodInvoker(meth, parameterExtractors))
          }.toMap

          CommandHandler(grpcMethodName, messageCodec, JavaPbAny.getDescriptor, methodInvokers)

        case _: DeleteServiceMethod =>
          val methodInvokers = serviceMethod.javaMethodOpt.map { meth =>
            (ProtobufEmptyTypeUrl, MethodInvoker(meth, Array.empty[ParameterExtractor[InvocationContext, AnyRef]]))
          }.toMap

          CommandHandler(grpcMethodName, messageCodec, Empty.getDescriptor, methodInvokers)
      }

    }
  }

  private def buildSyntheticMessageAndExtractors(
      nameGenerator: NameGenerator,
      serviceMethod: SyntheticRequestServiceMethod,
      entityKeys: Seq[String] = Seq.empty): (DescriptorProto, Map[Int, ExtractorCreator]) = {

    val inputMessageName = nameGenerator.getName(serviceMethod.methodName.capitalize + "KalixSyntheticRequest")

    val inputMessageDescriptor = DescriptorProto.newBuilder()
    inputMessageDescriptor.setName(inputMessageName)

    def addEntityKeyIfNeeded(paramName: String, fieldDescriptor: FieldDescriptorProto.Builder) =
      if (entityKeys.contains(paramName)) {
        val fieldOptions = kalix.FieldOptions.newBuilder().setEntityKey(true).build()
        val options =
          DescriptorProtos.FieldOptions
            .newBuilder()
            .setExtension(kalix.Annotations.field, fieldOptions)
            .build()

        fieldDescriptor.setOptions(options)
      }

    val indexedParams = serviceMethod.params.zipWithIndex
    val bodyField = indexedParams.collectFirst { case (BodyParameter(param, _), idx) =>
      val fieldDescriptor = FieldDescriptorProto.newBuilder()
      // todo ensure this is unique among field names
      fieldDescriptor.setName("json_body")
      // Always put the body at position 1 - even if there's no body, leave position 1 free. This keeps the body
      // parameter stable in case the user adds a body.
      fieldDescriptor.setNumber(1)
      fieldDescriptor.setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
      fieldDescriptor.setTypeName("google.protobuf.Any")
      inputMessageDescriptor.addField(fieldDescriptor)
      idx -> new ExtractorCreator {
        override def apply(descriptor: Descriptors.Descriptor): ParameterExtractor[DynamicMessageContext, AnyRef] = {
          new ParameterExtractors.BodyExtractor(descriptor.findFieldByNumber(1), param.getParameterType)
        }
      }
    }

    val pathParamFields = serviceMethod.parsedPath.fields.zipWithIndex.flatMap { case (paramName, fieldIdx) =>
      val (maybeParamIdx, paramType) = indexedParams
        .collectFirst {
          case (p: PathParameter, idx) if p.name == paramName =>
            Some(idx) -> p.param.getGenericParameterType
        }
        .getOrElse(None -> classOf[String])

      val fieldDescriptor = FieldDescriptorProto.newBuilder()
      fieldDescriptor.setName(paramName)
      val fieldNumber = fieldIdx + 2
      fieldDescriptor.setNumber(fieldNumber)
      fieldDescriptor.setType(mapJavaTypeToProtobuf(paramType))
      addEntityKeyIfNeeded(paramName, fieldDescriptor)
      inputMessageDescriptor.addField(fieldDescriptor)
      maybeParamIdx.map(_ -> new ExtractorCreator {
        override def apply(descriptor: Descriptors.Descriptor): ParameterExtractor[DynamicMessageContext, AnyRef] = {
          new ParameterExtractors.FieldExtractor[AnyRef](descriptor.findFieldByNumber(fieldNumber), identity)
        }
      })
    }

    val queryFieldsOffset = pathParamFields.size + 2

    val queryFields = indexedParams
      .collect { case (qp: QueryParamParameter, idx) =>
        idx -> qp
      }
      .sortBy(_._2.name)
      .zipWithIndex
      .map { case ((paramIdx, param), fieldIdx) =>
        val fieldNumber = fieldIdx + queryFieldsOffset
        val fieldDescriptor = FieldDescriptorProto.newBuilder()
        fieldDescriptor.setName(param.name)
        fieldDescriptor.setNumber(fieldNumber)
        fieldDescriptor.setType(mapJavaTypeToProtobuf(param.param.getGenericParameterType))
        inputMessageDescriptor.addField(fieldDescriptor)
        addEntityKeyIfNeeded(param.name, fieldDescriptor)
        paramIdx -> new ExtractorCreator {
          override def apply(descriptor: Descriptors.Descriptor): ParameterExtractor[DynamicMessageContext, AnyRef] = {
            new ParameterExtractors.FieldExtractor[AnyRef](descriptor.findFieldByNumber(fieldNumber), identity)
          }
        }
      }

    val inputProto = inputMessageDescriptor.build()
    val extractors = (bodyField.toSeq ++ pathParamFields ++ queryFields).toMap
    (inputProto, extractors)
  }

  private def mapJavaTypeToProtobuf(javaType: Type): FieldDescriptorProto.Type = {
    // todo make this smarter, eg, customizable parameter deserializers, UUIDs, byte arrays, enums etc
    if (javaType == classOf[String]) {
      FieldDescriptorProto.Type.TYPE_STRING
    } else if (javaType == classOf[java.lang.Long]) {
      FieldDescriptorProto.Type.TYPE_INT64
    } else if (javaType == classOf[java.lang.Integer]) {
      FieldDescriptorProto.Type.TYPE_INT32
    } else if (javaType == classOf[java.lang.Double]) {
      FieldDescriptorProto.Type.TYPE_DOUBLE
    } else if (javaType == classOf[java.lang.Float]) {
      FieldDescriptorProto.Type.TYPE_FLOAT
    } else if (javaType == classOf[java.lang.Boolean]) {
      FieldDescriptorProto.Type.TYPE_BOOL
    } else if (javaType == classOf[ByteString]) {
      FieldDescriptorProto.Type.TYPE_BYTES
    } else {
      throw new RuntimeException("Don't know how to extract type " + javaType + " from path.")
    }
  }

  private def buildHttpRule(serviceMethod: SyntheticRequestServiceMethod) = {
    val httpRule = HttpRule.newBuilder()
    val pathTemplate = serviceMethod.pathTemplate
    serviceMethod.requestMethod match {
      case RequestMethod.GET =>
        httpRule.setGet(pathTemplate)
      case RequestMethod.POST =>
        httpRule.setPost(pathTemplate)
      case RequestMethod.PUT =>
        httpRule.setPut(pathTemplate)
      case RequestMethod.PATCH =>
        httpRule.setPatch(pathTemplate)
      case RequestMethod.DELETE =>
        httpRule.setDelete(pathTemplate)
      case other =>
        httpRule.setCustom(
          CustomHttpPattern
            .newBuilder()
            .setKind(other.name())
            .setPath(pathTemplate))
    }
    httpRule
  }

  private def buildGrpcMethod(
      grpcMethodName: String,
      inputTypeName: String,
      streamIn: Boolean,
      streamOut: Boolean): MethodDescriptorProto.Builder =
    MethodDescriptorProto
      .newBuilder()
      .setName(grpcMethodName)
      .setInputType(inputTypeName)
      .setClientStreaming(streamIn)
      .setServerStreaming(streamOut)
      .setOutputType("google.protobuf.Any")

}

private[springsdk] final case class ComponentDescriptor private (
    serviceName: String,
    packageName: String,
    commandHandlers: Map[String, CommandHandler],
    serviceDescriptor: Descriptors.ServiceDescriptor,
    fileDescriptor: Descriptors.FileDescriptor)
