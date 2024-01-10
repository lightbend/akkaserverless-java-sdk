/*
 * Copyright 2024 Lightbend Inc.
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

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.adapter._
import com.google.protobuf.any.Any.toJavaProto
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.javasdk.JsonSupport
import kalix.javasdk.JsonSupport.decodeJson
import kalix.javasdk.action.ActionCreationContext
import kalix.javasdk.action.ReflectiveActionProvider
import kalix.javasdk.action.TestESSubscriptionAction
import kalix.javasdk.eventsourcedentity.OldTestESEvent.OldEvent1
import kalix.javasdk.eventsourcedentity.OldTestESEvent.OldEvent2
import kalix.javasdk.eventsourcedentity.OldTestESEvent.OldEvent3
import kalix.javasdk.eventsourcedentity.TestESEvent.Event4
import kalix.javasdk.impl.action.ActionService
import kalix.javasdk.impl.action.ActionsImpl
import kalix.protocol.action.ActionCommand
import kalix.protocol.action.ActionResponse
import kalix.protocol.action.Actions
import kalix.protocol.component.Reply
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ActionsImplSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside
    with OptionValues
    with ScalaFutures {

  private val classicSystem = system.toClassic

  def create(provider: ReflectiveActionProvider[_], messageCodec: MessageCodec): Actions = {
    val actionFactory: ActionFactory = ctx => provider.newRouter(ctx)
    val service = new ActionService(actionFactory, provider.serviceDescriptor(), Array(), messageCodec, None)

    val services = Map(provider.serviceDescriptor().getFullName -> service)

    //setting tracing as disabled, emulating that is discovered from the proxy.
    ProxyInfoHolder(system).overrideTracingCollectorEndpoint("")

    new ActionsImpl(classicSystem, services, new AbstractContext(classicSystem) {})
  }

  "The action service" should {
    "check event migration for subscription" in {
      val jsonMessageCodec = new JsonMessageCodec()
      val actionProvider = ReflectiveActionProvider.of(
        classOf[TestESSubscriptionAction],
        jsonMessageCodec,
        (_: ActionCreationContext) => new TestESSubscriptionAction)

      val service = create(actionProvider, jsonMessageCodec)
      val serviceName = actionProvider.serviceDescriptor().getFullName

      val event1 = jsonMessageCodec.encodeScala(new OldEvent1("state"))
      val reply1 = service.handleUnary(toActionCommand(serviceName, event1)).futureValue
      //ignore event1
      reply1.response shouldBe ActionResponse.Response.Empty

      val event2 = new JsonMessageCodec().encodeScala(new OldEvent2(123))
      val reply2 = service.handleUnary(toActionCommand(serviceName, event2)).futureValue
      inside(reply2.response) { case ActionResponse.Response.Reply(Reply(Some(payload), _, _)) =>
        decodeJson(classOf[Integer], toJavaProto(payload)) shouldBe 321 //migration reverts numbers
      }

      val event3 = new JsonMessageCodec().encodeScala(new OldEvent3(true))
      val reply3 = service.handleUnary(toActionCommand(serviceName, event3)).futureValue
      inside(reply3.response) { case ActionResponse.Response.Reply(Reply(Some(payload), _, _)) =>
        decodeJson(classOf[Boolean], toJavaProto(payload)) shouldBe true
      }

      val event4OldVersionNumber = JsonSupport.encodeJson(new Event4("value"), classOf[Event4].getName + "#1")
      val event4 =
        new JsonMessageCodec().encodeScala(event4OldVersionNumber)
      val reply4 = service.handleUnary(toActionCommand(serviceName, event4)).futureValue
      inside(reply4.response) { case ActionResponse.Response.Reply(Reply(Some(payload), _, _)) =>
        decodeJson(classOf[String], toJavaProto(payload)) shouldBe "value-v2" //-v2 from migration
      }
    }
  }

  private def toActionCommand(serviceName: String, event1: ScalaPbAny) = {
    ActionCommand(serviceName, "KalixSyntheticMethodOnESEs", Some(event1))
  }

}
