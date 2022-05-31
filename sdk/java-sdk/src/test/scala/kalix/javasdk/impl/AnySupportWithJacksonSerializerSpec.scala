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

package kalix.javasdk.impl

import akkaserverless.javasdk.action.EchoAction
import kalix.serializer.Serializer
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.{ Any => JavaPbAny }
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AnySupportWithJacksonSerializerSpec extends AnyWordSpec with Matchers with OptionValues {

  def newAnySupport(descriptors: Array[Descriptors.FileDescriptor], serializers: Map[Class[_], Serializer]) =
    new AnySupport(descriptors, getClass.getClassLoader, "com.example", prefer = AnySupport.Prefer.Java, serializers)

  "Any support for Java" should {

    "serialize and deserializer Jackson type to proto" in {

      import akkaserverless.javasdk.action.Number
      import akkaserverless.javasdk.action.Message

      val descriptor = ProtoDescriptorGenerator.generateFileDescriptorAction(classOf[EchoAction])
      val serializers = Serializer.buildSerializers(getClass.getClassLoader, descriptor)

      val anySupportWithSerializer = newAnySupport(Array(descriptor), serializers)

      val number = new Number(10)
      val encodedNumber = anySupportWithSerializer.encodeScala(number)
      val decodedNumber = anySupportWithSerializer.decodeMessage(encodedNumber).asInstanceOf[Number]
      decodedNumber.value shouldBe number.value

      val message = new Message("foo")
      val encodedMsg = anySupportWithSerializer.encodeScala(message)
      val decodedMsg = anySupportWithSerializer.decodeMessage(encodedMsg).asInstanceOf[Message]
      decodedMsg.value shouldBe message.value
    }
  }

}
