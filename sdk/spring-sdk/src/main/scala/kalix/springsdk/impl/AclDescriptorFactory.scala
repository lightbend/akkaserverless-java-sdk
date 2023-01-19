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

import akka.http.javadsl.model.StatusCode

import java.lang.reflect.Method
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import io.grpc.Status
import kalix.PrincipalMatcher
import kalix.springsdk.annotations.Acl
import kalix.springsdk.annotations.Acl.DenyStatusCode
import kalix.{ Acl => ProtoAcl }
import kalix.{ Annotations => KalixAnnotations }
import org.slf4j.LoggerFactory

object AclDescriptorFactory {

  private val logger = LoggerFactory.getLogger(classOf[AclDescriptorFactory.type])

  val invalidAnnotationUsage: String =
    "Invalid annotation usage. Matcher has both 'principal' and 'service' defined. " +
    "Only one is allowed."

  private def validateMatcher(matcher: Acl.Matcher): Unit = {
    if (matcher.principal() != Acl.Principal.UNSPECIFIED && matcher.service().nonEmpty)
      throw new IllegalArgumentException(invalidAnnotationUsage)
  }

  private def deriveProtoAnnotation(aclJavaAnnotation: Acl): ProtoAcl = {

    aclJavaAnnotation.allow().foreach(matcher => validateMatcher(matcher))
    aclJavaAnnotation.deny().foreach(matcher => validateMatcher(matcher))

    val aclBuilder = ProtoAcl.newBuilder()

    aclJavaAnnotation.allow.zipWithIndex.foreach { case (allow, idx) =>
      val principalMatcher = PrincipalMatcher.newBuilder()
      allow.principal match {
        case Acl.Principal.ALL =>
          principalMatcher.setPrincipal(PrincipalMatcher.Principal.ALL)
        case Acl.Principal.INTERNET =>
          principalMatcher.setPrincipal(PrincipalMatcher.Principal.INTERNET)
        case Acl.Principal.UNSPECIFIED =>
          principalMatcher.setService(allow.service())
      }

      aclBuilder.addAllow(idx, principalMatcher)
    }

    aclJavaAnnotation.deny.zipWithIndex.foreach { case (deny, idx) =>
      val principalMatcher = PrincipalMatcher.newBuilder()
      deny.principal match {
        case Acl.Principal.ALL =>
          principalMatcher.setPrincipal(PrincipalMatcher.Principal.ALL)
        case Acl.Principal.INTERNET =>
          principalMatcher.setPrincipal(PrincipalMatcher.Principal.INTERNET)
        case Acl.Principal.UNSPECIFIED =>
          principalMatcher.setService(deny.service())

      }
      aclBuilder.addDeny(idx, principalMatcher)
    }

    aclBuilder.setDenyCode(denyCodeHTTPtogRPC(aclJavaAnnotation.denyCode()))

    aclBuilder.build()
  }

  /**
   * It translates HTTP status codes from {@code DenyStatusCode} to GRPC codes (see
   * https://grpc.github.io/grpc/core/md_doc_statuscodes.html). Note: it is only maps the common statuses between gRPC
   * and normal HTTP Note: If 'Inherited', indicates that the code should be inherited from the parent (regardless of
   * the inherit field).
   * @param code
   */
  def denyCodeHTTPtogRPC(code: DenyStatusCode): Int = {
    val map: Map[DenyStatusCode, Int] =
      Map(
        Acl.DenyStatusCode.INHERITED -> 0,
        Acl.DenyStatusCode.BAD_REQUEST_400 -> Status.Code.INVALID_ARGUMENT.value(),
        Acl.DenyStatusCode.FORBIDDEN_403 -> Status.Code.PERMISSION_DENIED.value(),
        Acl.DenyStatusCode.NOT_FOUND_404 -> Status.Code.NOT_FOUND.value(),
        Acl.DenyStatusCode.AUTHENTICATION_REQUIRED_407 -> Status.Code.UNAUTHENTICATED.value(),
        Acl.DenyStatusCode.CONFLICT_409 -> Status.Code.ALREADY_EXISTS.value(),
        Acl.DenyStatusCode.INTERNAL_SERVER_ERROR_500 -> Status.Code.INTERNAL.value(),
        Acl.DenyStatusCode.SERVICE_UNAVAILABLE_503 -> Status.Code.UNAVAILABLE.value(),
        Acl.DenyStatusCode.GATEWAY_TIMEOUT_504 -> Status.Code.DEADLINE_EXCEEDED.value())
    map(code)
  }

  def defaultAclFileDescriptor(cls: Class[_]): Option[DescriptorProtos.FileDescriptorProto] = {

    Option.when(cls.getAnnotation(classOf[Acl]) != null) {
      // do we need to recurse into the dependencies of the dependencies? Probably not, just top level imports.
      val dependencies: Array[Descriptors.FileDescriptor] = Array(KalixAnnotations.getDescriptor)

      val policyFile = "kalix_policy.proto"

      val protoBuilder =
        DescriptorProtos.FileDescriptorProto.newBuilder
          .setName(policyFile)
          .setSyntax("proto3")
          .setPackage("kalix.springsdk")

      val kalixFileOptions = kalix.FileOptions.newBuilder
      kalixFileOptions.setAcl(deriveProtoAnnotation(cls.getAnnotation(classOf[Acl])))

      val options =
        DescriptorProtos.FileOptions
          .newBuilder()
          .setExtension(kalix.Annotations.file, kalixFileOptions.build())
          .build()

      protoBuilder.setOptions(options)
      val fdProto = protoBuilder.build
      val fd = Descriptors.FileDescriptor.buildFrom(fdProto, dependencies)
      if (logger.isDebugEnabled) {
        logger.debug(
          "Generated file descriptor for service [{}]: \n{}",
          policyFile,
          ProtoDescriptorRenderer.toString(fd))
      }
      fd.toProto
    }
  }

  def serviceLevelAclAnnotation(component: Class[_]): Option[kalix.ServiceOptions] = {

    val javaAclAnnotation = component.getAnnotation(classOf[Acl])

    Option.when(javaAclAnnotation != null) {
      val kalixServiceOptions = kalix.ServiceOptions.newBuilder()
      kalixServiceOptions.setAcl(deriveProtoAnnotation(javaAclAnnotation))
      kalixServiceOptions.build()
    }
  }

  def methodLevelAclAnnotation(method: Method): Option[kalix.MethodOptions] = {

    val javaAclAnnotation = method.getAnnotation(classOf[Acl])

    Option.when(javaAclAnnotation != null) {
      val kalixServiceOptions = kalix.MethodOptions.newBuilder()
      kalixServiceOptions.setAcl(deriveProtoAnnotation(javaAclAnnotation))
      kalixServiceOptions.build()
    }
  }

}
