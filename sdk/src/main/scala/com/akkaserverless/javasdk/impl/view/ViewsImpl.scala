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

package com.akkaserverless.javasdk.impl.view

import java.util.Optional
import scala.compat.java8.OptionConverters._
import scala.util.control.NonFatal
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.akkaserverless.javasdk.{Context, Metadata, Reply, Service, ServiceCallFactory}
import com.akkaserverless.javasdk.impl._
import com.akkaserverless.javasdk.impl.reply.MessageReplyImpl
import com.akkaserverless.javasdk.view.UpdateHandlerContext
import com.akkaserverless.javasdk.view.ViewContext
import com.akkaserverless.javasdk.view.ViewFactory
import com.akkaserverless.protocol.{view => pv}
import com.google.protobuf.Descriptors
import com.google.protobuf.any.{Any => ScalaPbAny}
import com.google.protobuf.{Any => JavaPbAny}
import org.slf4j.LoggerFactory

/** INTERNAL API */
final class ViewService(val factory: Optional[ViewFactory],
                        override val descriptor: Descriptors.ServiceDescriptor,
                        val anySupport: AnySupport,
                        val viewId: String)
    extends Service {

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory.asScala.collect {
      case resolved: ResolvedEntityFactory => resolved.resolvedMethods
    }

  override final val componentType = pv.Views.name

  override def entityType: String = viewId
}

object ViewsImpl {
  private val log = LoggerFactory.getLogger(classOf[ViewsImpl])
}

/** INTERNAL API */
final class ViewsImpl(system: ActorSystem, _services: Map[String, ViewService], rootContext: Context) extends pv.Views {
  import ViewsImpl.log

  private final val services = _services.iterator.toMap

  /**
   * Handle a full duplex streamed session. One stream will be established per incoming message to the view service.
   *
   *
   * The first message is ReceiveEvent and contain the request metadata, including the service name and command
   * name.
   */
  override def handle(
      in: akka.stream.scaladsl.Source[pv.ViewStreamIn, akka.NotUsed]
  ): akka.stream.scaladsl.Source[pv.ViewStreamOut, akka.NotUsed] =
    // FIXME: see akkaserverless-framework/issues/209 and akkaserverless-framework/issues/207
    // It is currently only implemented to support one request (ReceiveEvent) with one response (Upsert).
    // The intention, and reason for full-duplex streaming, is that there should be able to have an interaction
    // with two main types of operations, loads, and updates, and with
    // each load there is an associated continuation, which in turn may return more operations, including more loads,
    // and so on recursively.
    in.prefixAndTail(1)
      .flatMapConcat {
        case (Seq(pv.ViewStreamIn(pv.ViewStreamIn.Message.Receive(receiveEvent), _)), tail) =>
          services.get(receiveEvent.serviceName) match {
            case Some(service: ViewService) =>
              if (!service.factory.isPresent)
                throw new IllegalArgumentException(
                  s"Unexpected call to service [${receiveEvent.serviceName}] with viewId [${service.viewId}]: " +
                    "this view has `transform_updates=false` set, so updates should be handled entirely by the proxy " +
                    "and not reach the user function"
                )

              val handler = service.factory.get.create(new ViewContextImpl(service.viewId))

              val state: Optional[JavaPbAny] =
                receiveEvent.bySubjectLookupResult match {
                  case Some(row) => row.value.map(ScalaPbAny.toJavaProto).asJava
                  case None => Optional.empty
                }

              val commandName = receiveEvent.commandName
              val msg = ScalaPbAny.toJavaProto(receiveEvent.payload.get)
              val metadata = new MetadataImpl(receiveEvent.metadata.map(_.entries.toVector).getOrElse(Nil))
              val context = new HandlerContextImpl(service.viewId, commandName, metadata, state)

              val reply = try {
                handler.handle(msg, context)
              } catch {
                case e: ViewException => throw e
                case NonFatal(error) =>
                  throw ViewException(context, s"View unexpected failure: ${error.getMessage}", Some(error))
              }

              Source.single(replyToOut(reply, receiveEvent))

            case None =>
              val errMsg = s"Unknown service: ${receiveEvent.serviceName}"
              log.error(errMsg)
              Source.failed(new RuntimeException(errMsg))
          }

        case (Seq(), _) =>
          log.warn("View stream closed before init.")
          Source.empty[pv.ViewStreamOut]

        case (Seq(pv.ViewStreamIn(other, _)), _) =>
          val errMsg =
            s"Akka Serverless protocol failure: expected ReceiveEvent message, but got ${other.getClass.getName}"
          Source.failed(new RuntimeException(errMsg))
      }

  private def replyToOut(reply: Reply[JavaPbAny], receiveEvent: pv.ReceiveEvent): pv.ViewStreamOut = {
    val table = receiveEvent.initialTable
    val key = receiveEvent.key
    val upsert = {
      reply match {
        case r: MessageReplyImpl[JavaPbAny] =>
          pv.Upsert(Some(pv.Row(table, key, Some(ScalaPbAny.fromJavaProto(r.payload)))))
        case _ =>
          // ignore incoming event
          pv.Upsert(None)
      }
    }
    pv.ViewStreamOut(pv.ViewStreamOut.Message.Upsert(upsert))
  }

  trait AbstractContext extends ViewContext {
    override def serviceCallFactory(): ServiceCallFactory = rootContext.serviceCallFactory()
  }

  private final class HandlerContextImpl(override val viewId: String,
                                         override val commandName: String,
                                         override val metadata: Metadata,
                                         override val state: Optional[JavaPbAny])
      extends UpdateHandlerContext
      with AbstractContext
      with StateContext {

    override def eventSubject(): Optional[String] =
      if (metadata.isCloudEvent)
        metadata.asCloudEvent().subject()
      else
        Optional.empty()
  }

  private final class ViewContextImpl(override val viewId: String) extends ViewContext with AbstractContext

}

/** INTERNAL API */
trait StateContext {
  def state: Optional[JavaPbAny]
}
