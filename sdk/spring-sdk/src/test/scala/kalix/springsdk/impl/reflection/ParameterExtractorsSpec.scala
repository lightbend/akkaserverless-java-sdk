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

package kalix.springsdk.impl.reflection

import scala.reflect.ClassTag

import com.google.protobuf.ByteString
import com.google.protobuf.DynamicMessage
import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.{ Any => JavaPbAny }
import kalix.javasdk.JsonSupport
import kalix.springsdk.impl.ComponentDescriptor
import kalix.springsdk.impl.InvocationContext
import kalix.springsdk.impl.SpringSdkMessageCodec
import kalix.springsdk.impl.reflection.ParameterExtractors.BodyExtractor
import kalix.springsdk.testmodels.Message
import kalix.springsdk.testmodels.action.EchoAction
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ParameterExtractorsSpec extends AnyWordSpec with Matchers {

  def descriptorFor[T](implicit ev: ClassTag[T]): ComponentDescriptor =
    ComponentDescriptor.descriptorFor(ev.runtimeClass, new SpringSdkMessageCodec)

  "BodyExtractor" should {

    "extract json payload from Any" in {
      val componentDescriptor = descriptorFor[EchoAction]
      val method = componentDescriptor.commandHandlers("MessageBody")

      val jsonBody = JsonSupport.encodeJson(new Message("test"))

      val field = method.requestMessageDescriptor.findFieldByNumber(1)
      val message = DynamicMessage
        .newBuilder(method.requestMessageDescriptor)
        .setField(field, jsonBody)
        .build()

      val wrappedMessage = ScalaPbAny().withValue(message.toByteString)

      val javaMethod = method.methodInvokers.values.head
      val bodyExtractor: BodyExtractor[_] =
        javaMethod.parameterExtractors.collect { case extractor: BodyExtractor[_] => extractor }.head

      val context = InvocationContext(wrappedMessage, method.requestMessageDescriptor)
      bodyExtractor.extract(context)

    }

    "reject non json payload" in {
      val componentDescriptor = descriptorFor[EchoAction]

      val method = componentDescriptor.commandHandlers("MessageBody")

      val nonJsonBody =
        JavaPbAny
          .newBuilder()
          .setTypeUrl("something.empty")
          .setValue(ByteString.EMPTY)
          .build()

      val field = method.requestMessageDescriptor.findFieldByNumber(1)
      val message = DynamicMessage
        .newBuilder(method.requestMessageDescriptor)
        .setField(field, nonJsonBody)
        .build()

      val wrappedMessage = ScalaPbAny().withValue(message.toByteString)
      val javaMethod = method.methodInvokers.values.head
      val bodyExtractor: BodyExtractor[_] =
        javaMethod.parameterExtractors.collect { case extractor: BodyExtractor[_] => extractor }.head

      val context = InvocationContext(wrappedMessage, method.requestMessageDescriptor)

      intercept[IllegalArgumentException] {
        bodyExtractor.extract(context)
      }

    }
  }

}
