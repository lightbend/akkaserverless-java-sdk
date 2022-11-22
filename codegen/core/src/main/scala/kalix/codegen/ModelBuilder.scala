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

package kalix.codegen

import scala.jdk.CollectionConverters._

import kalix.CodegenOptions
import kalix.EventSourcedEntityDef
import kalix.ReplicatedEntityDef
import kalix.ServiceOptions.ServiceType
import kalix.ValueEntityDef
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.ServiceDescriptor

/**
 * Builds a model of entities and their properties from a protobuf descriptor
 */
object ModelBuilder {

  /**
   * Convertor from Descriptor to ProtoMessageType. Abstract because its implementation is different between Java and
   * Scala.
   *
   * (an alternative implementation could have been to pass the descriptor into ProtoMessageType and put the logic on
   * the 'read side', but that makes testing with programmatically-generated names harder)
   */
  abstract class ProtoMessageTypeExtractor {
    def apply(descriptor: Descriptors.GenericDescriptor): ProtoMessageType
    def fileDescriptorObject(descriptor: Descriptors.GenericDescriptor): ProtoMessageType
    def packageName(descriptor: Descriptors.GenericDescriptor): PackageNaming
  }

  object Model {
    def empty: Model = Model(Map.empty, Map.empty)

    def fromService(service: Service): Model =
      Model.empty.addService(service)

    def fromEntity(entity: Entity): Model =
      Model.empty.addEntity(entity)
  }

  /**
   * The Kalix service definitions and entities that could be extracted from a protobuf descriptor
   */
  case class Model(services: Map[String, Service], entities: Map[String, Entity]) {
    def lookupEntity(service: EntityService): Entity = {
      entities.getOrElse(
        service.componentFullName,
        throw new IllegalArgumentException(
          "Service [" + service.messageType.fullyQualifiedProtoName + "] refers to entity [" + service.componentFullName +
          s"], but no entity configuration is found for that component name. Entities: [${entities.keySet.mkString(", ")}]"))
    }

    def addService(service: Service): Model =
      copy(services + (service.messageType.fullyQualifiedProtoName -> service), entities)

    def addEntity(entity: Entity): Model =
      copy(services, entities + (entity.messageType.fullyQualifiedProtoName -> entity))

    def ++(model: Model): Model =
      Model(services ++ model.services, entities ++ model.entities)
  }

  /**
   * An entity represents the primary model object and is conceptually equivalent to a class, or a type of state.
   */
  sealed abstract class Entity(val messageType: ProtoMessageType, val entityType: String) {
    val abstractEntityName = "Abstract" + messageType.name
    val routerName = messageType.name + "Router"
    val providerName = messageType.name + "Provider"
    val provider = messageType.deriveName(_ + "Provider")
    val impl = messageType.deriveName(identity)
  }

  /**
   * A type of Entity that stores its state using a journal of events, and restores its state by replaying that journal.
   */
  case class EventSourcedEntity(
      override val messageType: ProtoMessageType,
      override val entityType: String,
      state: State,
      events: Iterable[Event])
      extends Entity(messageType, entityType)

  /**
   * A type of Entity that stores its current state directly.
   */
  case class ValueEntity(override val messageType: ProtoMessageType, override val entityType: String, state: State)
      extends Entity(messageType, entityType)

  /**
   * A type of Entity that replicates its current state using CRDTs.
   */
  case class ReplicatedEntity(
      override val messageType: ProtoMessageType,
      override val entityType: String,
      data: ReplicatedData)
      extends Entity(messageType, entityType)

  /**
   * The underlying replicated data type for a Replicated Entity.
   */
  sealed abstract class ReplicatedData(val shortName: String, val typeArguments: Iterable[TypeArgument]) {
    def this(shortName: String, typeArguments: TypeArgument*) = this(shortName, typeArguments)

    val name: String = "Replicated" + shortName
    val baseClass: String = name + "Entity"
  }

  case object ReplicatedCounter extends ReplicatedData("Counter")

  case class ReplicatedRegister(value: TypeArgument) extends ReplicatedData("Register", value)

  case class ReplicatedSet(element: TypeArgument) extends ReplicatedData("Set", element)

  case class ReplicatedMap(key: TypeArgument) extends ReplicatedData("Map", key)

  case class ReplicatedCounterMap(key: TypeArgument) extends ReplicatedData("CounterMap", key)

  case class ReplicatedRegisterMap(key: TypeArgument, value: TypeArgument)
      extends ReplicatedData("RegisterMap", key, value)

  case class ReplicatedMultiMap(key: TypeArgument, value: TypeArgument) extends ReplicatedData("MultiMap", key, value)

  case object ReplicatedVote extends ReplicatedData("Vote")

  /**
   * Type argument for generic replicated data types with type parameters.
   */
  sealed trait TypeArgument

  object TypeArgument {

    def apply(messageType: ProtoMessageType): TypeArgument =
      apply(messageType.name, messageType.parent, messageType.descriptorObject)

    def apply(name: String, proto: PackageNaming, messageTypeOpt: Option[ProtoMessageType]): TypeArgument = {
      if (name.nonEmpty && name.charAt(0).isLower) ScalarTypeArgument(ScalarType(name))
      else MessageTypeArgument(ProtoMessageType(name, name, proto, messageTypeOpt))
    }
  }

  /**
   * Type argument for Protobuf message types.
   */
  case class MessageTypeArgument(messageType: MessageType) extends TypeArgument

  /**
   * Type argument for Protobuf scalar types.
   */
  case class ScalarTypeArgument(scalar: ScalarType) extends TypeArgument

  sealed trait ScalarType

  object ScalarType {
    case object Double extends ScalarType
    case object Float extends ScalarType
    case object Int32 extends ScalarType
    case object Int64 extends ScalarType
    case object UInt32 extends ScalarType
    case object UInt64 extends ScalarType
    case object SInt32 extends ScalarType
    case object SInt64 extends ScalarType
    case object Fixed32 extends ScalarType
    case object Fixed64 extends ScalarType
    case object SFixed32 extends ScalarType
    case object SFixed64 extends ScalarType
    case object Bool extends ScalarType
    case object String extends ScalarType
    case object Bytes extends ScalarType
    case object Unknown extends ScalarType

    def isScalarType(protoType: String): Boolean = apply(protoType) != Unknown

    def apply(protoType: String): ScalarType =
      protoType match {
        case "double"   => Double
        case "float"    => Float
        case "int32"    => Int32
        case "int64"    => Int64
        case "uint32"   => UInt32
        case "uint64"   => UInt64
        case "sint32"   => SInt32
        case "sint64"   => SInt64
        case "fixed32"  => Fixed32
        case "fixed64"  => Fixed64
        case "sfixed32" => SFixed32
        case "sfixed64" => SFixed64
        case "bool"     => Bool
        case "string"   => String
        case "bytes"    => Bytes
        case _          => Unknown
      }
  }

  /**
   * A Service backed by Kalix; either an Action, View or Entity
   */
  sealed abstract class Service(val messageType: ProtoMessageType, val commands: Iterable[Command]) {
    lazy val commandTypes =
      commands.flatMap { cmd =>
        cmd.inputType :: cmd.outputType :: Nil
      }
  }

  /**
   * A Service backed by an Action - a serverless function that is executed based on a trigger. The trigger could be an
   * HTTP or gRPC request or a stream of messages or events.
   */
  case class ActionService(
      override val messageType: ProtoMessageType,
      override val commands: Iterable[Command],
      userDefinedNameOpt: Option[ProtoMessageType])
      extends Service(messageType, commands) {

    val (baseClassName, className) =
      userDefinedNameOpt match {
        case Some(userDefinedName) => (userDefinedName.name, userDefinedName.name)
        case _ =>
          val baseCls = if (messageType.name.endsWith("Action")) messageType.name else messageType.name + "Action"
          val cls = if (messageType.name.contains("Action")) messageType.name + "Impl" else messageType.name + "Action"
          (baseCls, cls)
      }

    val impl = messageType.deriveName(_ => className)
    val abstractActionName = "Abstract" + baseClassName
    val routerName = baseClassName + "Router"
    val providerName = baseClassName + "Provider"
    val provider = messageType.deriveName(_ => baseClassName + "Provider")

    val classNameQualified = s"${messageType.parent.javaPackage}.$className"
    val providerNameQualified = s"${messageType.parent.javaPackage}.$providerName"
  }

  /**
   * A Service backed by a View, which provides a way to retrieve state from multiple Entities based on a query. You can
   * query non-key data items. You can create views from Value Entity state, Event Sourced Entity events, and by
   * subscribing to topics.
   */
  case class ViewService(
      override val messageType: ProtoMessageType,
      /** all commands - queries and updates */
      override val commands: Iterable[Command],
      viewId: String,
      /** all updates, also non-transformed */
      updates: Iterable[Command],
      transformedUpdates: Iterable[Command],
      queries: Iterable[Command],
      userDefinedNameOpt: Option[ProtoMessageType])
      extends Service(messageType, commands) {

    val (baseClassName, className) =
      userDefinedNameOpt match {
        case Some(userDefinedName) => (userDefinedName.name, userDefinedName.name)
        case _ =>
          val baseCls = if (messageType.name.endsWith("View")) messageType.name else messageType.name + "View"
          val cls = if (messageType.name.contains("View")) messageType.name + "Impl" else messageType.name + "View"
          (baseCls, cls)
      }

    val impl = messageType.deriveName(_ => className)
    val abstractViewName = "Abstract" + baseClassName
    val abstractView = messageType.deriveName(_ => abstractViewName)
    val routerName = baseClassName + "Router"
    val providerName = baseClassName + "Provider"
    val provider = messageType.deriveName(_ => baseClassName + "Provider")

    val classNameQualified = s"${messageType.parent.javaPackage}.$className"
    val providerNameQualified = s"${messageType.parent.javaPackage}.$providerName"

    if (updates.isEmpty)
      throw new IllegalArgumentException(
        s"At least one view method must have `option (kalix.method).view.update` in ${messageType.protoName} (${messageType.parent.protoFileName}).")

    updates.toSeq
      .diff(transformedUpdates.toSeq)
      .filterNot(_.handleDeletes)
      .find(command => command.inputType.fullyQualifiedProtoName != command.outputType.fullyQualifiedProtoName)
      .foreach { command =>
        throw new IllegalStateException(
          s"A View method ${command.name} with input param ${command.inputType.fullyQualifiedProtoName} " +
          s"and different output param ${command.outputType.fullyQualifiedProtoName} " +
          s"must have `option (kalix.method).view.update.transform_updates` equals `true`")
      }

    val stateTypes: Seq[ProtoMessageType] = updates.map(_.outputType).toSeq.distinct
  }

  /**
   * A Service backed by a Kalix Entity
   */
  case class EntityService(
      override val messageType: ProtoMessageType,
      override val commands: Iterable[Command],
      componentFullName: String)
      extends Service(messageType, commands)

  /**
   * A command is used to express the intention to alter the state of an Entity.
   */
  case class Command(
      name: String,
      inputType: ProtoMessageType,
      outputType: ProtoMessageType,
      streamedInput: Boolean,
      streamedOutput: Boolean,
      inFromTopic: Boolean,
      outToTopic: Boolean,
      ignore: Boolean,
      handleDeletes: Boolean) {

    def isUnary: Boolean = !streamedInput && !streamedOutput
    def isStreamIn: Boolean = streamedInput && !streamedOutput
    def isStreamOut: Boolean = !streamedInput && streamedOutput
    def isStreamInOut: Boolean = streamedInput && streamedOutput
    def hasStream: Boolean = isStreamIn || isStreamOut || isStreamInOut

  }

  object Command {
    def from(method: Descriptors.MethodDescriptor)(implicit messageExtractor: ProtoMessageTypeExtractor): Command = {
      val eventing = method.getOptions.getExtension(kalix.Annotations.method).getEventing
      Command(
        method.getName,
        messageExtractor(method.getInputType),
        messageExtractor(method.getOutputType),
        streamedInput = method.isClientStreaming,
        streamedOutput = method.isServerStreaming,
        inFromTopic = eventing.hasIn && eventing.getIn.hasTopic,
        outToTopic = eventing.hasOut && eventing.getOut.hasTopic,
        ignore = eventing.hasIn && eventing.getIn.getIgnore,
        handleDeletes = eventing.hasIn && eventing.getIn.getHandleDeletes)
    }
  }

  /**
   * An event indicates that a change has occurred to an entity. Events are stored in a journal, and are read and
   * replayed each time the entity is reloaded by the Kalix state management system.
   */
  case class Event(messageType: MessageType)

  /**
   * The state is simply data — the current set of values for an entity instance. Event Sourced entities hold their
   * state in memory.
   */
  case class State(messageType: MessageType)

  /**
   * Given a protobuf descriptor, discover the Kalix entities and their properties.
   *
   * Impure.
   *
   * @param descriptors
   *   the protobuf descriptors containing service entities
   * @param additionalDescriptors
   *   additional descriptors, e.g. from imports
   * @return
   *   the entities found
   */
  def introspectProtobufClasses(
      descriptors: Iterable[Descriptors.FileDescriptor],
      additionalDescriptors: Iterable[Descriptors.FileDescriptor] = Seq.empty)(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): Model = {

    val descriptorSeq = descriptors.toSeq
    val additionalDescriptorSeq = (descriptorSeq.toSet ++ additionalDescriptors.toSet).toSeq

    descriptorSeq.foldLeft(Model.empty) { case (accModel, fileDescriptor) =>
      log.debug("Looking at descriptor " + fileDescriptor.getName)

      val modelFromServices =
        fileDescriptor.getServices.asScala.foldLeft(accModel) { (model, serviceDescriptor) =>
          if (serviceDescriptor.getOptions.hasExtension(kalix.Annotations.codegen)) {
            model ++ modelFromCodegenOptions(serviceDescriptor, additionalDescriptorSeq)

          } else if (serviceDescriptor.getOptions.hasExtension(kalix.Annotations.service)) {
            // FIXME: old format, builds service model from old service annotation
            model ++ modelFromServiceOptions(serviceDescriptor)
          } else {
            model
          }
        }

      // FIXME: old format, builds entity model from domain.proto file
      val modelFromDomainFile =
        extractEventSourcedEntityDefinitionFromFileOptions(fileDescriptor, additionalDescriptorSeq) ++
        extractValueEntityDefinitionFromFileOptions(fileDescriptor, additionalDescriptorSeq) ++
        extractReplicatedEntityDefinitionFromFileOptions(fileDescriptor)

      modelFromServices ++ modelFromDomainFile
    }
  }

  /**
   * @return
   *   the FQN for a proto 'message' (which are used not just for "messages", but also for state types etc)
   */
  private def resolveProtoMessageType(
      name: String,
      descriptor: Descriptors.FileDescriptor,
      descriptors: Seq[Descriptors.FileDescriptor])(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): ProtoMessageType = {
    // TODO this is used in the java tck as ValueEntity state type - I'm not sure we want to
    // support this? In that case we should probably support all primitives?
    if (name == "String")
      ProtoMessageType.noDescriptor("String", PackageNaming("", "", "", javaMultipleFiles = true))
    else {
      val fullName = resolveFullName(descriptor.getPackage, name)
      val protoPackage = fullName.split("\\.").init.mkString(".")
      val protoName = fullName.split("\\.").last
      // TODO we could also look at the imports in the proto file to support
      // importing names from outside this file without using their fully qualified name.
      descriptors
        .filter(_.getPackage == protoPackage)
        .flatMap(_.getMessageTypes.asScala)
        .filter(_.getName == protoName) match {
        case Nil =>
          throw new IllegalStateException(
            s"No descriptor found for [$fullName] (searched: [${descriptors.map(_.getFile.getName).mkString(", ")}])")
        case Seq(descriptor) =>
          messageExtractor.apply(descriptor)
        case matchingDescriptors =>
          throw new IllegalStateException(
            s"Multiple matching descriptors found for [$fullName] (searched: [${descriptors
              .map(_.getFile.getName)
              .mkString(", ")}], found in: ${matchingDescriptors.map(_.getFile.getName).mkString(", ")})")
      }
    }
  }

  /**
   * Lookup a FileDescriptor for the passed `package` and `name`.
   *
   * Valid inputs are:
   *   - package: foo.bar.baz, name: Foo
   *   - package: foo.bar, name: .baz.Foo
   *
   * The above input will trigger a lookup for a descriptor defining package "foo.bar.baz" and message "Foo"
   */
  private def lookupDomainDescriptor(
      resolvedPackage: String,
      resolvedName: String,
      additionalDescriptors: Seq[Descriptors.FileDescriptor]): Descriptors.FileDescriptor = {

    // we should have only one match for package and resolvedName
    // if more than one proto defines the same message and package it will be caught earlier, by protoc
    additionalDescriptors
      .find { desc =>
        desc.getPackage == resolvedPackage && desc.getMessageTypes.asScala.exists(_.getName == resolvedName)
      }
      .getOrElse {
        throw new IllegalArgumentException(
          s"No descriptor found declaring package [$resolvedPackage] and message [$resolvedName]")
      }
  }

  /**
   * Resolves a proto 'message' using `package` and `name`.
   *
   * Valid inputs are:
   *   - package: foo.bar.baz, name: Foo
   *   - package: foo.bar, name: .baz.Foo
   *
   * The above input will trigger a lookup for a descriptor defining package "foo.bar.baz" and message "Foo"
   *
   * @return
   *   the FQN for a proto 'message' (which are used not just for "messages", but also for state types etc)
   */
  private def resolveMessageType(name: String, pkg: String, additionalDescriptors: Seq[Descriptors.FileDescriptor])(
      implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): MessageType = {

    val (revolvedPackage, resolvedName) = extractPackageAndName(pkg, name)

    val descriptor = lookupDomainDescriptor(revolvedPackage, resolvedName, additionalDescriptors)
    resolveProtoMessageType(resolvedName, descriptor, additionalDescriptors)
  }

  private def resolveTypeArgument(name: String, pkg: String, additionalDescriptors: Seq[Descriptors.FileDescriptor])(
      implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): TypeArgument = {

    val (revolvedPackage, resolvedName) = extractPackageAndName(pkg, name)

    if (ScalarType.isScalarType(resolvedName))
      ScalarTypeArgument(ScalarType(resolvedName))
    else {
      val descriptor = lookupDomainDescriptor(revolvedPackage, resolvedName, additionalDescriptors)
      MessageTypeArgument(resolveProtoMessageType(resolvedName, descriptor, additionalDescriptors))
    }
  }

  /**
   * Resolves the provided name relative to the provided package
   *
   * @param name
   *   the name to resolve
   * @param pkg
   *   the package to resolve relative to
   * @return
   *   the resolved full name
   */
  private[codegen] def resolveFullName(pkg: String, name: String) =
    name.indexOf('.') match {
      case 0 => // name starts with a dot, treat as relative to package
        s"$pkg$name"
      case -1 => // name contains no dots, prepend package
        s"$pkg.$name"
      case _ => // name contains at least one dot, treat as absolute
        name
    }

  /**
   * Resolves the provided name relative to the provided package and split it back into name and package
   *
   * (foo.bar, baz.Foo) => (foo.bar.baz, Foo)
   */
  private def extractPackageAndName(pkg: String, name: String): (String, String) = {
    val resolvedPackageAndName = resolveFullName(pkg, name)
    val idx = resolvedPackageAndName.lastIndexOf('.')
    val resolvedName = resolvedPackageAndName.drop(idx + 1)
    val resolvedPackage = resolvedPackageAndName.take(idx)
    (resolvedPackage, resolvedName)
  }

  private def modelFromCodegenOptions(
      serviceDescriptor: ServiceDescriptor,
      additionalDescriptors: Seq[Descriptors.FileDescriptor])(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): Model = {

    val codegenOptions = serviceDescriptor.getOptions.getExtension(kalix.Annotations.codegen)
    val serviceName = messageExtractor(serviceDescriptor)
    val methods = serviceDescriptor.getMethods.asScala
    val commands = methods.map(Command.from)

    codegenOptions.getCodegenCase match {
      case CodegenOptions.CodegenCase.ACTION =>
        val userDefinedName = buildUserDefinedMessageType(codegenOptions.getAction.getName, serviceName)
        val actionService =
          ActionService(serviceName.asJavaMultiFiles, commands, userDefinedName)
        Model.fromService(actionService)

      case CodegenOptions.CodegenCase.VIEW =>
        val methodDetails = methods.flatMap { method =>
          Option(method.getOptions.getExtension(kalix.Annotations.method).getView).map(viewOptions =>
            (method, viewOptions))
        }
        val updates = methodDetails.collect {
          case (method, viewOptions) if viewOptions.hasUpdate =>
            Command.from(method)
        }
        val transformedUpdates = methodDetails
          .collect {
            case (method, viewOptions) if viewOptions.hasUpdate && viewOptions.getUpdate.getTransformUpdates =>
              Command.from(method)
          }
          .filterNot(_.ignore)
        val queries = methodDetails.collect {
          case (method, viewOptions) if viewOptions.hasQuery =>
            Command.from(method)
        }

        val userDefinedName = buildUserDefinedMessageType(codegenOptions.getView.getName, serviceName)
        val viewService =
          ViewService(
            serviceName.asJavaMultiFiles,
            commands,
            viewId = serviceDescriptor.getName,
            updates = updates,
            transformedUpdates = transformedUpdates,
            queries = queries,
            userDefinedName)
        Model.fromService(viewService)

      case CodegenOptions.CodegenCase.VALUE_ENTITY =>
        val entityDef = codegenOptions.getValueEntity
        val componentFullName = resolveFullComponentName(entityDef.getName, serviceName)
        Model
          .fromService(EntityService(serviceName, commands, componentFullName))
          .addEntity(extractValueEntity(serviceDescriptor, entityDef, additionalDescriptors))

      case CodegenOptions.CodegenCase.EVENT_SOURCED_ENTITY =>
        val entityDef = codegenOptions.getEventSourcedEntity
        val componentFullName = resolveFullComponentName(entityDef.getName, serviceName)
        Model
          .fromService(EntityService(serviceName, commands, componentFullName))
          .addEntity(extractEventSourcedEntity(serviceDescriptor, entityDef, additionalDescriptors))

      case CodegenOptions.CodegenCase.REPLICATED_ENTITY =>
        val entityDef = codegenOptions.getReplicatedEntity
        val componentFullName = resolveFullComponentName(entityDef.getName, serviceName)
        Model
          .fromService(EntityService(serviceName, commands, componentFullName))
          .addEntity(extractReplicatedEntity(serviceDescriptor, entityDef, additionalDescriptors))

      case _ => Model.empty
    }

  }

  /* if optionalName is empty (or null), full component name will be the same as the Service
   * otherwise, we need to resolve the entity name.
   */
  private def resolveFullComponentName(optionalName: String, serviceName: ProtoMessageType) = {
    val messageType = defineEntityMessageType(optionalName, serviceName)
    resolveFullName(messageType.parent.protoPackage, messageType.name)
  }

  private def nonEmptyName(name: String) =
    Option(name).filter(_.trim.nonEmpty)

  private def buildUserDefinedMessageType(optionalName: String, serviceName: ProtoMessageType) =
    nonEmptyName(optionalName)
      .map { name =>
        // if filled, we need to resolve the name
        // for example: pkg = foo.bar, name = baz.Qux
        // becomes: pkg = foo.bar.baz, name = Qux
        val (resolvedPackage, resolvedName) = extractPackageAndName(serviceName.parent.javaPackage, name)
        val descOpt = serviceName.descriptorObject.map { messageType =>
          messageType.copy(parent = messageType.parent.asJavaMultiFiles.changePackages(resolvedPackage))
        }
        val packageNaming =
          serviceName.parent.asJavaMultiFiles.changePackages(resolvedPackage)
        ProtoMessageType(resolvedName, resolvedName, packageNaming, descOpt)
      }

  private def defineEntityMessageType(optionalName: String, serviceName: ProtoMessageType) =
    buildUserDefinedMessageType(optionalName, serviceName)
      .getOrElse {
        // when an entity name is not explicitly defined, we need to fabricate a unique name
        // that doesn't conflict with the service name (since we do generate a grpc service for it)
        // therefore we append 'Entity' to the name
        serviceName
          .deriveName(_ + "Entity")
          .copy(protoName = serviceName.protoName + "Entity")
      }

  private def extractEventSourcedEntity(
      serviceProtoDescriptor: ServiceDescriptor,
      entityDef: EventSourcedEntityDef,
      additionalDescriptors: Seq[Descriptors.FileDescriptor])(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): EventSourcedEntity = {

    val protoPackageName = serviceProtoDescriptor.getFile.getPackage

    EventSourcedEntity(
      defineEntityMessageType(entityDef.getName, messageExtractor(serviceProtoDescriptor)),
      entityDef.getEntityType,
      State(resolveMessageType(entityDef.getState, protoPackageName, additionalDescriptors)),
      entityDef.getEventsList.asScala.map { event =>
        Event(resolveMessageType(event, protoPackageName, additionalDescriptors))
      })

  }

  private def extractValueEntity(
      serviceProtoDescriptor: ServiceDescriptor,
      entityDef: ValueEntityDef,
      additionalDescriptors: Seq[Descriptors.FileDescriptor])(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): ValueEntity = {

    val protoPackageName = serviceProtoDescriptor.getFile.getPackage
    ValueEntity(
      defineEntityMessageType(entityDef.getName, messageExtractor(serviceProtoDescriptor)),
      entityDef.getEntityType,
      State(resolveMessageType(entityDef.getState, protoPackageName, additionalDescriptors)))
  }

  private def extractReplicatedEntity(
      serviceProtoDescriptor: ServiceDescriptor,
      entityDef: ReplicatedEntityDef,
      additionalDescriptors: Seq[Descriptors.FileDescriptor])(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): ReplicatedEntity = {

    import ReplicatedEntityDef.ReplicatedDataCase

    val protoPackageName = serviceProtoDescriptor.getFile.getPackage

    val dataType =
      entityDef.getReplicatedDataCase match {
        case ReplicatedDataCase.REPLICATED_COUNTER => ReplicatedCounter
        case ReplicatedDataCase.REPLICATED_REGISTER =>
          val typeArgument =
            resolveTypeArgument(entityDef.getReplicatedRegister.getValue, protoPackageName, additionalDescriptors)
          ReplicatedRegister(typeArgument)

        case ReplicatedDataCase.REPLICATED_SET =>
          val typeArgument =
            resolveTypeArgument(entityDef.getReplicatedSet.getElement, protoPackageName, additionalDescriptors)
          ReplicatedSet(typeArgument)

        case ReplicatedDataCase.REPLICATED_MAP =>
          val typeArgument =
            resolveTypeArgument(entityDef.getReplicatedMap.getKey, protoPackageName, additionalDescriptors)
          ReplicatedMap(typeArgument)

        case ReplicatedDataCase.REPLICATED_COUNTER_MAP =>
          val typeArgument =
            resolveTypeArgument(entityDef.getReplicatedCounterMap.getKey, protoPackageName, additionalDescriptors)
          ReplicatedCounterMap(typeArgument)

        case ReplicatedDataCase.REPLICATED_REGISTER_MAP =>
          val typeArgumentKey =
            resolveTypeArgument(entityDef.getReplicatedRegisterMap.getKey, protoPackageName, additionalDescriptors)
          val typeArgumentValue =
            resolveTypeArgument(entityDef.getReplicatedRegisterMap.getValue, protoPackageName, additionalDescriptors)
          ReplicatedRegisterMap(typeArgumentKey, typeArgumentValue)

        case ReplicatedDataCase.REPLICATED_MULTI_MAP =>
          val typeArgumentKey =
            resolveTypeArgument(entityDef.getReplicatedMultiMap.getKey, protoPackageName, additionalDescriptors)
          val typeArgumentValue =
            resolveTypeArgument(entityDef.getReplicatedMultiMap.getValue, protoPackageName, additionalDescriptors)
          ReplicatedMultiMap(typeArgumentKey, typeArgumentValue)

        case ReplicatedDataCase.REPLICATED_VOTE =>
          ReplicatedVote

        case ReplicatedDataCase.REPLICATEDDATA_NOT_SET =>
          throw new IllegalArgumentException("Replicated data type not set")
      }

    ReplicatedEntity(
      defineEntityMessageType(entityDef.getName, messageExtractor(serviceProtoDescriptor)),
      entityDef.getEntityType,
      dataType)
  }

  private def modelFromServiceOptions(serviceDescriptor: Descriptors.ServiceDescriptor)(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): Model = {

    val serviceOptions = serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
    val serviceType = serviceOptions.getType
    val serviceName = messageExtractor(serviceDescriptor)

    val methods = serviceDescriptor.getMethods.asScala
    val commands = methods.map(Command.from)

    serviceType match {
      case ServiceType.SERVICE_TYPE_ENTITY =>
        if (serviceOptions.getComponent eq null) Model.empty
        else {
          val componentName = serviceOptions.getComponent
          val componentFullName = resolveFullName(serviceDescriptor.getFile.getPackage, componentName)
          Model.fromService(EntityService(serviceName, commands, componentFullName))
        }
      case ServiceType.SERVICE_TYPE_ACTION =>
        Model.fromService(ActionService(serviceName.asJavaMultiFiles, commands, None))

      case ServiceType.SERVICE_TYPE_VIEW =>
        val methodDetails = methods.flatMap { method =>
          Option(method.getOptions.getExtension(kalix.Annotations.method).getView).map(viewOptions =>
            (method, viewOptions))
        }
        val updates = methodDetails.collect {
          case (method, viewOptions) if viewOptions.hasUpdate =>
            Command.from(method)
        }
        Model.fromService(
          ViewService(
            serviceName.asJavaMultiFiles,
            commands,
            viewId = serviceDescriptor.getName,
            updates = updates,
            transformedUpdates = methodDetails
              .collect {
                case (method, viewOptions) if viewOptions.hasUpdate && viewOptions.getUpdate.getTransformUpdates =>
                  Command.from(method)
              },
            queries = methodDetails.collect {
              case (method, viewOptions) if viewOptions.hasQuery =>
                Command.from(method)
            },
            None))

      case _ => Model.empty
    }
  }

  /**
   * Extracts any defined event sourced entity from the provided protobuf file descriptor
   *
   * @param descriptor
   *   the file descriptor to extract from
   * @return
   *   the event sourced entity
   */
  private def extractEventSourcedEntityDefinitionFromFileOptions(
      descriptor: Descriptors.FileDescriptor,
      additionalDescriptors: Seq[Descriptors.FileDescriptor])(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): Model = {

    val entityDef =
      descriptor.getOptions
        .getExtension(kalix.Annotations.file)
        .getEventSourcedEntity

    val protoReference = messageExtractor.packageName(descriptor)
    val fullQualifiedDescriptor = Some(messageExtractor.fileDescriptorObject(descriptor.getFile))

    nonEmptyName(entityDef.getName)
      .map { name =>
        Model.fromEntity(
          EventSourcedEntity(
            ProtoMessageType(name, name, protoReference.asJavaMultiFiles, fullQualifiedDescriptor),
            entityDef.getEntityType,
            State(resolveProtoMessageType(entityDef.getState, descriptor, additionalDescriptors)),
            entityDef.getEventsList.asScala
              // TODO this assumes events are defined in the same proto as the entity. To lift this restriction,
              // use something like resolveFullyQualifiedMessageType above
              .map(event => Event(ProtoMessageType(event, event, protoReference, fullQualifiedDescriptor)))))
      }
      .getOrElse(Model.empty)
  }

  /**
   * Extracts any defined value entity from the provided protobuf file descriptor
   *
   * @param descriptor
   *   the file descriptor to extract from
   */
  private def extractValueEntityDefinitionFromFileOptions(
      descriptor: Descriptors.FileDescriptor,
      descriptors: Seq[Descriptors.FileDescriptor])(implicit
      log: Log,
      messageExtractor: ProtoMessageTypeExtractor): Model = {
    val entityDef =
      descriptor.getOptions
        .getExtension(kalix.Annotations.file)
        .getValueEntity

    nonEmptyName(entityDef.getName)
      .map { name =>
        Model.fromEntity(
          ValueEntity(
            ProtoMessageType(
              name,
              name,
              messageExtractor.packageName(descriptor).asJavaMultiFiles,
              Some(messageExtractor.fileDescriptorObject(descriptor.getFile))),
            entityDef.getEntityType,
            State(resolveProtoMessageType(entityDef.getState, descriptor, descriptors))))
      }
      .getOrElse(Model.empty)
  }

  /**
   * Extracts any defined replicated entity from the provided protobuf file descriptor
   *
   * @param descriptor
   *   the file descriptor to extract from
   */
  private def extractReplicatedEntityDefinitionFromFileOptions(
      descriptor: Descriptors.FileDescriptor)(implicit log: Log, messageExtractor: ProtoMessageTypeExtractor): Model = {
    import kalix.ReplicatedEntity.ReplicatedDataCase

    val entityDef =
      descriptor.getOptions
        .getExtension(kalix.Annotations.file)
        .getReplicatedEntity

    val protoReference = messageExtractor.packageName(descriptor)
    val fullQualifiedDescriptor = Some(messageExtractor.fileDescriptorObject(descriptor.getFile))

    nonEmptyName(entityDef.getName)
      .flatMap { name =>
        val dataType = entityDef.getReplicatedDataCase match {
          case ReplicatedDataCase.REPLICATED_COUNTER =>
            Some(ReplicatedCounter)
          case ReplicatedDataCase.REPLICATED_REGISTER =>
            val value = TypeArgument(entityDef.getReplicatedRegister.getValue, protoReference, fullQualifiedDescriptor)
            Some(ReplicatedRegister(value))
          case ReplicatedDataCase.REPLICATED_SET =>
            val element = TypeArgument(entityDef.getReplicatedSet.getElement, protoReference, fullQualifiedDescriptor)
            Some(ReplicatedSet(element))
          case ReplicatedDataCase.REPLICATED_MAP =>
            val key = TypeArgument(entityDef.getReplicatedMap.getKey, protoReference, fullQualifiedDescriptor)
            Some(ReplicatedMap(key))
          case ReplicatedDataCase.REPLICATED_COUNTER_MAP =>
            val key = TypeArgument(entityDef.getReplicatedCounterMap.getKey, protoReference, fullQualifiedDescriptor)
            Some(ReplicatedCounterMap(key))
          case ReplicatedDataCase.REPLICATED_REGISTER_MAP =>
            val key = TypeArgument(entityDef.getReplicatedRegisterMap.getKey, protoReference, fullQualifiedDescriptor)
            val value =
              TypeArgument(entityDef.getReplicatedRegisterMap.getValue, protoReference, fullQualifiedDescriptor)
            Some(ReplicatedRegisterMap(key, value))
          case ReplicatedDataCase.REPLICATED_MULTI_MAP =>
            val key = TypeArgument(entityDef.getReplicatedMultiMap.getKey, protoReference, fullQualifiedDescriptor)
            val value = TypeArgument(entityDef.getReplicatedMultiMap.getValue, protoReference, fullQualifiedDescriptor)
            Some(ReplicatedMultiMap(key, value))
          case ReplicatedDataCase.REPLICATED_VOTE =>
            Some(ReplicatedVote)
          case ReplicatedDataCase.REPLICATEDDATA_NOT_SET =>
            None
        }
        dataType
          .map { data =>
            Model.fromEntity(
              ReplicatedEntity(
                ProtoMessageType(name, name, protoReference.asJavaMultiFiles, fullQualifiedDescriptor),
                entityDef.getEntityType,
                data))
          }
      }
      .getOrElse(Model.empty)
  }
}
