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

package com.akkaserverless.javasdk.impl.action

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.javadsl.Source
import akka.stream.scaladsl.Sink
import com.akkaserverless.javasdk.Context
import com.akkaserverless.javasdk.ServiceCallFactory
import com.akkaserverless.javasdk.action.Action
import com.akkaserverless.javasdk.action.MessageEnvelope
import com.akkaserverless.javasdk.actionspec.ActionspecApi
import com.akkaserverless.javasdk.impl.AbstractContext
import com.akkaserverless.javasdk.impl.AnySupport
import com.akkaserverless.javasdk.impl.MetadataImpl
import com.akkaserverless.javasdk.impl.ResolvedServiceCall
import com.akkaserverless.javasdk.impl.ResolvedServiceCallFactory
import com.akkaserverless.javasdk.impl.ResolvedServiceMethod
import com.akkaserverless.javasdk.impl.effect.SideEffectImpl
import com.akkaserverless.protocol.action.ActionCommand
import com.akkaserverless.protocol.action.ActionResponse
import com.akkaserverless.protocol.action.Actions
import com.akkaserverless.protocol.component.Reply
import com.google.protobuf
import com.google.protobuf.any.{ Any => ScalaPbAny }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

class ActionHandlerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with Inside with OptionValues {

  private implicit val system = ActorSystem("ActionHandlerSpec")

  import system.dispatcher

  private val serviceDescriptor =
    ActionspecApi.getDescriptor.findServiceByName("ActionSpecService")
  private val serviceName = serviceDescriptor.getFullName
  private val anySupport = new AnySupport(Array(ActionspecApi.getDescriptor), this.getClass.getClassLoader)
  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  def create(handler: ActionHandler[_]): Actions = {
    val service = new ActionService(_ => handler, serviceDescriptor, anySupport)

    val services = Map(serviceName -> service)
    val scf = new ResolvedServiceCallFactory(services)

    new ActionsImpl(system, services, new AbstractContext(scf, system) {})
  }

  "The action service" should {
    "invoke unary commands" in {
      val service = create(new AbstractHandler {

        override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Action.Effect[Any] =
          createReplyEffect("out: " + extractInField(message))
      })

      val reply =
        Await.result(service.handleUnary(ActionCommand(serviceName, "Unary", createInPayload("in"))), 10.seconds)

      inside(reply.response) { case ActionResponse.Response.Reply(Reply(payload, _, _)) =>
        extractOutField(payload) should ===("out: in")
      }
    }

    "invoke streamed in commands" in {
      val service = create(new AbstractHandler {
        override def handleStreamedIn(
            commandName: String,
            stream: Source[MessageEnvelope[Any], NotUsed]): Action.Effect[Any] =
          createAsyncReplyEffect(
            stream.asScala
              .map(extractInField)
              .runWith(Sink.seq)
              .map(ins => createReplyEffect("out: " + ins.mkString(", "))))
      })

      val reply = Await.result(
        service.handleStreamedIn(
          akka.stream.scaladsl.Source
            .single(ActionCommand(serviceName, "StreamedIn"))
            .concat(
              akka.stream.scaladsl.Source(1 to 3).map(idx => ActionCommand(payload = createInPayload(s"in $idx"))))),
        10.seconds)

      inside(reply.response) { case ActionResponse.Response.Reply(Reply(payload, _, _)) =>
        extractOutField(payload) should ===("out: in 1, in 2, in 3")
      }
    }

    "invoke streamed out commands" in {
      val service = create(new AbstractHandler {
        override def handleStreamedOut(
            commandName: String,
            message: MessageEnvelope[Any]): Source[Action.Effect[_], NotUsed] = {
          val in = extractInField(message)
          akka.stream.scaladsl
            .Source(1 to 3)
            .asJava
            .map(idx => createReplyEffect(s"out $idx: $in"))
            .asInstanceOf[Source[Action.Effect[_], NotUsed]]
        }
      })

      val replies = Await.result(
        service
          .handleStreamedOut(ActionCommand(serviceName, "Unary", createInPayload("in")))
          .runWith(Sink.seq),
        10.seconds)

      replies.zipWithIndex.foreach { case (reply, idx) =>
        inside(reply.response) { case ActionResponse.Response.Reply(Reply(payload, _, _)) =>
          extractOutField(payload) should ===(s"out ${idx + 1}: in")
        }
      }
    }

    "invoke streamed commands" in {
      val service = create(new AbstractHandler {
        override def handleStreamed(
            commandName: String,
            stream: Source[MessageEnvelope[Any], NotUsed]): Source[Action.Effect[_], NotUsed] =
          stream.asScala
            .map(extractInField)
            .map(in => createReplyEffect(s"out: $in"))
            .asJava
            .asInstanceOf[Source[Action.Effect[_], NotUsed]]
      })

      val replies = Await.result(
        service
          .handleStreamed(
            akka.stream.scaladsl.Source
              .single(ActionCommand(serviceName, "StreamedIn"))
              .concat(
                akka.stream.scaladsl.Source(1 to 3).map(idx => ActionCommand(payload = createInPayload(s"in $idx")))))
          .runWith(Sink.seq),
        10.seconds)

      replies.zipWithIndex.foreach { case (reply, idx) =>
        inside(reply.response) { case ActionResponse.Response.Reply(Reply(payload, _, _)) =>
          extractOutField(payload) should ===(s"out: in ${idx + 1}")
        }
      }
    }

    "pass over side effects from an outer async effect to the inner one" in {
      val dummyResolvedMethod = ResolvedServiceMethod(
        serviceDescriptor.getMethods.get(0),
        anySupport.resolveTypeDescriptor(serviceDescriptor.getMethods.get(0).getInputType),
        anySupport.resolveTypeDescriptor(serviceDescriptor.getMethods.get(0).getOutputType))

      val service = create(new AbstractHandler {

        override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Action.Effect[Any] = {
          createAsyncReplyEffect(Future {
            createReplyEffect("reply").addSideEffect(
              SideEffectImpl(
                ResolvedServiceCall(dummyResolvedMethod, anySupport.encodeJava(message.payload()), MetadataImpl.Empty),
                false))
          }).addSideEffect(SideEffectImpl(
            ResolvedServiceCall(dummyResolvedMethod, anySupport.encodeJava(message.payload()), MetadataImpl.Empty),
            true))
        }
      })

      val reply =
        Await.result(service.handleUnary(ActionCommand(serviceName, "Unary", createInPayload("in"))), 10.seconds)

      reply match {
        case ActionResponse(_, sideEffects, _) =>
          sideEffects should have size 2
      }

    }

  }

  private def createOutAny(field: String): Any =
    ActionspecApi.Out.newBuilder().setField(field).build()

  private def createReplyEffect(field: String): Action.Effect[Any] =
    ActionEffectImpl.ReplyEffect(createOutAny(field), None, Nil)

  private def createAsyncReplyEffect(future: Future[Action.Effect[Any]]): Action.Effect[Any] =
    ActionEffectImpl.AsyncEffect(future, Nil)

  private def extractInField(message: MessageEnvelope[Any]) =
    message.payload().asInstanceOf[ActionspecApi.In].getField

  private def createInPayload(field: String) =
    Some(ScalaPbAny.fromJavaProto(protobuf.Any.pack(ActionspecApi.In.newBuilder().setField(field).build())))

  private def extractOutField(payload: Option[ScalaPbAny]) =
    ScalaPbAny.toJavaProto(payload.value).unpack(classOf[ActionspecApi.Out]).getField

  class TestAction extends Action

  private abstract class AbstractHandler extends ActionHandler[TestAction](new TestAction) {
    override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Action.Effect[Any] =
      ???

    def handleStreamedOut(commandName: String, message: MessageEnvelope[Any]): Source[Action.Effect[_], NotUsed] = ???

    override def handleStreamedIn(
        commandName: String,
        stream: Source[MessageEnvelope[Any], NotUsed]): Action.Effect[Any] =
      ???

    def handleStreamed(
        commandName: String,
        stream: Source[MessageEnvelope[Any], NotUsed]): Source[Action.Effect[_], NotUsed] = ???
  }

}
