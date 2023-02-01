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

import com.google.api.{ AnnotationsProto => HttpAnnotationsProto }
import com.google.protobuf.AnyProto
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.EmptyProto
import com.google.protobuf.TimestampProto
import kalix.{ Annotations => KalixAnnotations }
import org.slf4j.LoggerFactory

private[impl] object ProtoDescriptorGenerator {

  private val logger = LoggerFactory.getLogger(classOf[ProtoDescriptorGenerator.type])

  // do we need to recurse into the dependencies of the dependencies? Probably not, just top level imports.
  private val dependencies: Array[Descriptors.FileDescriptor] =
    Array(
      AnyProto.getDescriptor,
      EmptyProto.getDescriptor,
      TimestampProto.getDescriptor,
      HttpAnnotationsProto.getDescriptor,
      KalixAnnotations.getDescriptor)

  def genFileDescriptor(
      name: String,
      packageName: String,
      service: DescriptorProtos.ServiceDescriptorProto,
      messages: Set[DescriptorProtos.DescriptorProto]): Descriptors.FileDescriptor = {

    val protoBuilder = DescriptorProtos.FileDescriptorProto.newBuilder
    protoBuilder
      .setName(name + ".proto") // FIXME: snake_case this ?!
      .setSyntax("proto3")
      .setPackage(packageName)
      .setOptions(DescriptorProtos.FileOptions.newBuilder.setJavaMultipleFiles(true).build)

    protoBuilder.addDependency("google/protobuf/any.proto")
    protoBuilder.addDependency("google/protobuf/empty.proto")
    protoBuilder.addDependency("google/protobuf/timestamp.proto")
    protoBuilder.addService(service)
    messages.foreach(protoBuilder.addMessageType)

    // finally build all final descriptor
    val fd = Descriptors.FileDescriptor.buildFrom(protoBuilder.build, dependencies)
    if (logger.isDebugEnabled) {
      logger.debug("Generated file descriptor for service [{}]: \n{}", name, ProtoDescriptorRenderer.toString(fd))
    }
    fd
  }
}
