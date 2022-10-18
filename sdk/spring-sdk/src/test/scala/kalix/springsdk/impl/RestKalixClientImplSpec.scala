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

import com.google.protobuf.{ ByteString, Descriptors, DynamicMessage }
import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.{ Any => JavaPbAny }
import kalix.javasdk.{ DeferredCall, JsonSupport }
import kalix.javasdk.impl.{ AnySupport, RestDeferredCallImpl }
import kalix.protocol.discovery.IdentificationInfo
import kalix.springsdk.testmodels.action.ActionsTestModels.{
  GetClassLevel,
  GetWithOneParam,
  GetWithOneQueryParam,
  GetWithoutParam,
  PostWithOneQueryParam,
  PostWithTwoParam,
  PostWithoutParam
}
import kalix.springsdk.testmodels.Message
import org.scalatest
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.{ CollectionHasAsScala, MapHasAsScala }

class RestKalixClientImplSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach with ComponentDescriptorSuite {

  var restKalixClient: RestKalixClientImpl = _
  val messageCodec = new SpringSdkMessageCodec

  override def beforeEach(): Unit = {
    restKalixClient = new RestKalixClientImpl(new SpringSdkMessageCodec)
    restKalixClient.setIdentificationInfo(
      IdentificationInfo(serviceIdentificationHeader = "testServiceHeader", selfDeploymentName = "testName"))
  }

  "The Rest Kalix Client" should {
    "return a DeferredCall for a simple GET request" in {
      val actionWithGetNoParams = ComponentDescriptor.descriptorFor(classOf[GetWithoutParam], messageCodec)
      restKalixClient.registerComponent(actionWithGetNoParams.serviceDescriptor)

      val defCall = restKalixClient.get("/message", classOf[Message])

      assertRestDeferredCall(defCall) { restDefCall =>
        val targetMethod = actionWithGetNoParams.serviceDescriptor.findMethodByName("Message")
        restDefCall.methodDescriptor shouldBe targetMethod
        assertMethodParamsMatch(targetMethod, restDefCall.message)
        restDefCall.metadata.get("testServiceHeader").get() shouldBe "testName"
      }

    }

    "return a DeferredCall for a GET request with a path param" in {
      val actionWithGetOneParam = ComponentDescriptor.descriptorFor(classOf[GetWithOneParam], messageCodec)
      restKalixClient.registerComponent(actionWithGetOneParam.serviceDescriptor)

      val defCall = restKalixClient.get("/message/hello", classOf[Message])
      assertRestDeferredCall(defCall) { restDefCall =>
        val targetMethod = actionWithGetOneParam.serviceDescriptor.findMethodByName("Message")
        restDefCall.methodDescriptor shouldBe targetMethod
        assertMethodParamsMatch(targetMethod, restDefCall.message, "hello")
      }
    }

    "return a DeferredCall for a GET request with two path params" in {
      val actionWithTwoParams = ComponentDescriptor.descriptorFor(classOf[GetClassLevel], messageCodec)
      restKalixClient.registerComponent(actionWithTwoParams.serviceDescriptor)

      val defCall = restKalixClient.get("/action/test/message/2", classOf[Message])
      assertRestDeferredCall(defCall) { restDefCall =>
        val targetMethod = actionWithTwoParams.serviceDescriptor.findMethodByName("Message")
        restDefCall.methodDescriptor shouldBe targetMethod
        assertMethodParamsMatch(targetMethod, restDefCall.message, List("test", 2): _*)
      }
    }

    "return a DeferredCall for a simple POST request" in {
      val actionWithTwoParams = ComponentDescriptor.descriptorFor(classOf[PostWithoutParam], messageCodec)
      restKalixClient.registerComponent(actionWithTwoParams.serviceDescriptor)

      val msgSent = new Message("hello world")
      val defCall = restKalixClient.post("/message", msgSent, classOf[Message])
      assertRestDeferredCall(defCall) { restDefCall =>
        val targetMethod = actionWithTwoParams.serviceDescriptor.findMethodByName("Message")
        restDefCall.methodDescriptor shouldBe targetMethod
        restDefCall.metadata.get("testServiceHeader").get() shouldBe "testName"

        assertMethodBodyMatch(targetMethod, restDefCall.message) { body =>
          decodeJson(body, classOf[Message]).value shouldBe msgSent.value
        }
      }
    }

    "return a DeferredCall for a POST request with 2 params and body" in {
      val actionWithTwoParams = ComponentDescriptor.descriptorFor(classOf[PostWithTwoParam], messageCodec)
      restKalixClient.registerComponent(actionWithTwoParams.serviceDescriptor)

      val msgSent = new Message("hello world")
      val defCall = restKalixClient.post("/message/one/2", msgSent, classOf[Message])
      assertRestDeferredCall(defCall) { restDefCall =>
        val targetMethod = actionWithTwoParams.serviceDescriptor.findMethodByName("Message")
        restDefCall.methodDescriptor shouldBe targetMethod

        assertMethodParamsMatch(targetMethod, restDefCall.message, List("one", 2): _*)
        assertMethodBodyMatch(targetMethod, restDefCall.message) { body =>
          decodeJson(body, classOf[Message]).value shouldBe msgSent.value
        }

      }
    }

    "return a DeferredCall for a POST request when multiple methods are available" in {
      val actionPost = ComponentDescriptor.descriptorFor(classOf[PostWithoutParam], messageCodec)
      val actionGetOneParam = ComponentDescriptor.descriptorFor(classOf[GetWithOneParam], messageCodec)
      restKalixClient.registerComponent(actionPost.serviceDescriptor)
      restKalixClient.registerComponent(actionGetOneParam.serviceDescriptor)

      val msgSent = new Message("hello world")
      val defCall = restKalixClient.post("/message", msgSent, classOf[Message])
      assertRestDeferredCall(defCall) { restDefCall =>
        val targetMethod = actionPost.serviceDescriptor.findMethodByName("Message")
        restDefCall.methodDescriptor shouldBe targetMethod

        assertMethodBodyMatch(targetMethod, restDefCall.message) { body =>
          decodeJson(body, classOf[Message]).value shouldBe msgSent.value
        }
      }
    }

    "return a DeferredCall when using query params" in {
      val actionGet = ComponentDescriptor.descriptorFor(classOf[GetWithOneQueryParam], messageCodec)
      val actionPost = ComponentDescriptor.descriptorFor(classOf[PostWithOneQueryParam], messageCodec)
      restKalixClient.registerComponent(actionGet.serviceDescriptor)
      restKalixClient.registerComponent(actionPost.serviceDescriptor)

      val msgSent = new Message("hello world")
      val defCall = restKalixClient.post("/message?dest=john", msgSent, classOf[Message])
      assertRestDeferredCall(defCall) { restDefCall =>
        val targetMethod = actionPost.serviceDescriptor.findMethodByName("Message")
        restDefCall.methodDescriptor shouldBe targetMethod

        assertMethodParamsMatch(targetMethod, restDefCall.message, "john")
        assertMethodBodyMatch(targetMethod, restDefCall.message) { body =>
          decodeJson(body, classOf[Message]).value shouldBe msgSent.value
        }
      }
    }
  }

  private def assertRestDeferredCall[M, R](defCall: DeferredCall[M, R])(
      assertFunc: RestDeferredCallImpl[M, R] => scalatest.Assertion) = {
    defCall shouldBe a[RestDeferredCallImpl[ScalaPbAny, _]]

    withClue(defCall.getClass) {
      assertFunc(defCall.asInstanceOf[RestDeferredCallImpl[M, R]])
    }
  }

  private def assertMethodParamsMatch(
      targetMethod: Descriptors.MethodDescriptor,
      message: ScalaPbAny,
      methodArgs: Any*) = {
    message.typeUrl shouldBe AnySupport.DefaultTypeUrlPrefix + "/" + targetMethod.getInputType.getFullName

    val dynamicMessage = DynamicMessage.parseFrom(targetMethod.getInputType, message.value)
    targetMethod.getInputType.getFields.asScala
      .filter(_.getName != "json_body") // use assertMethodBodyMatch to compare the body
      .map(dynamicMessage.getField) shouldBe methodArgs
  }

  private def decodeJson[T](dm: DynamicMessage, cls: Class[T]): T = {
    val typeUrl = dm.getField(JavaPbAny.getDescriptor.findFieldByName("type_url")).asInstanceOf[String]
    val bytes = dm.getField(JavaPbAny.getDescriptor.findFieldByName("value")).asInstanceOf[ByteString]

    val any =
      JavaPbAny
        .newBuilder()
        .setTypeUrl(typeUrl)
        .setValue(bytes)
        .build()
    JsonSupport.decodeJson(cls, any)
  }

  private def assertMethodBodyMatch(targetMethod: Descriptors.MethodDescriptor, message: ScalaPbAny)(
      assertFunc: DynamicMessage => scalatest.Assertion) = {
    val dynamicMessage = DynamicMessage.parseFrom(targetMethod.getInputType, message.value)

    val bodyMsg = targetMethod.getInputType.getFields.asScala
      .find(_.getName == "json_body")
      .map(dynamicMessage.getField)
      .getOrElse(fail("failed to find body"))
      .asInstanceOf[DynamicMessage]

    assertFunc(bodyMsg)
  }

}
