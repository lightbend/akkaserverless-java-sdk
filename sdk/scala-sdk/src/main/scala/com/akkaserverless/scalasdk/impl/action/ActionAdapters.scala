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

package com.akkaserverless.scalasdk.impl.action

import java.util.Optional
import scala.jdk.CollectionConverters.SetHasAsJava
import scala.jdk.OptionConverters.RichOptional
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.javadsl.Source
import com.akkaserverless.javasdk
import com.akkaserverless.javasdk.impl.action.ActionOptionsImpl
import com.akkaserverless.scalasdk.Metadata
import com.akkaserverless.scalasdk.ServiceCallFactory
import com.akkaserverless.scalasdk.action.Action
import com.akkaserverless.scalasdk.action.ActionContext
import com.akkaserverless.scalasdk.action.ActionCreationContext
import com.akkaserverless.scalasdk.action.ActionProvider
import com.akkaserverless.scalasdk.action.MessageEnvelope
import com.akkaserverless.scalasdk.impl.MetadataImpl
import com.akkaserverless.scalasdk.impl.ScalaServiceCallFactoryAdapter
import com.google.protobuf.Descriptors

private[scalasdk] final case class JavaActionAdapter(scalaSdkAction: Action) extends javasdk.action.Action {

  /** INTERNAL API */
  override def _internalSetActionContext(context: Optional[javasdk.action.ActionContext]): Unit =
    scalaSdkAction._internalSetActionContext(context.map(new ScalaActionContextAdapter(_)).toScala)
}

private[scalasdk] case class JavaActionProviderAdapter[A <: Action](scalaSdkProvider: ActionProvider[A])
    extends javasdk.action.ActionProvider[javasdk.action.Action] {

  override def options(): javasdk.action.ActionOptions =
    ActionOptionsImpl(scalaSdkProvider.options.forwardHeaders.asJava)

  override def newHandler(javaSdkContext: javasdk.action.ActionCreationContext)
      : javasdk.impl.action.ActionHandler[javasdk.action.Action] = {
    val scalaSdkHandler = scalaSdkProvider.newHandler(ScalaActionCreationContextAdapter(javaSdkContext))
    JavaActionHandlerAdapter(JavaActionAdapter(scalaSdkHandler.action), scalaSdkHandler)
  }

  override def serviceDescriptor(): Descriptors.ServiceDescriptor =
    scalaSdkProvider.serviceDescriptor

  override def additionalDescriptors(): Array[Descriptors.FileDescriptor] =
    scalaSdkProvider.additionalDescriptors.toArray
}

private[scalasdk] case class JavaActionHandlerAdapter[A <: Action](
    javaSdkAction: javasdk.action.Action,
    scalaSdkHandler: ActionHandler[A])
    extends javasdk.impl.action.ActionHandler[javasdk.action.Action](javaSdkAction) {

  override def handleUnary(
      commandName: String,
      message: javasdk.action.MessageEnvelope[Any]): javasdk.action.Action.Effect[_] = {
    val messageEnvelopeAdapted = ScalaMessageEnvelopeAdapter(message)
    scalaSdkHandler.handleUnary(commandName, messageEnvelopeAdapted) match {
      case eff: ActionEffectImpl.PrimaryEffect[_] => eff.toJavaSdk
    }
  }
  override def handleStreamedOut(
      commandName: String,
      message: javasdk.action.MessageEnvelope[Any]): Source[javasdk.action.Action.Effect[_], NotUsed] = {

    val messageEnvelopeAdapted = ScalaMessageEnvelopeAdapter(message)
    val src = scalaSdkHandler.handleStreamedOut(commandName, messageEnvelopeAdapted)

    src.map { case eff: ActionEffectImpl.PrimaryEffect[_] =>
      eff.toJavaSdk
    }.asJava
  }

  override def handleStreamedIn(
      commandName: String,
      stream: Source[javasdk.action.MessageEnvelope[Any], NotUsed]): javasdk.action.Action.Effect[_] = {
    val convertedStream =
      stream
        .map(el => ScalaMessageEnvelopeAdapter(el))
        .asScala

    scalaSdkHandler.handleStreamedIn(commandName, convertedStream) match {
      case eff: ActionEffectImpl.PrimaryEffect[_] => eff.toJavaSdk
    }
  }

  override def handleStreamed(commandName: String, stream: Source[javasdk.action.MessageEnvelope[Any], NotUsed])
      : Source[javasdk.action.Action.Effect[_], NotUsed] = {

    val convertedStream =
      stream
        .map(el => ScalaMessageEnvelopeAdapter(el).asInstanceOf[MessageEnvelope[Any]])
        .asScala

    scalaSdkHandler
      .handleStreamed(commandName, convertedStream)
      .map { case eff: ActionEffectImpl.PrimaryEffect[_] =>
        eff.toJavaSdk
      }
      .asJava
  }

}

private[scalasdk] final case class ScalaMessageEnvelopeAdapter[A](javaSdkMsgEnvelope: javasdk.action.MessageEnvelope[A])
    extends MessageEnvelope[A] {
  override def metadata: Metadata = new MetadataImpl(
    // FIXME can we get rid of this cast?
    javaSdkMsgEnvelope.metadata().asInstanceOf[com.akkaserverless.javasdk.impl.MetadataImpl])
  override def payload: A = javaSdkMsgEnvelope.payload()
}

private[scalasdk] final case class ScalaActionCreationContextAdapter(
    javaSdkCreationContext: javasdk.action.ActionCreationContext)
    extends ActionCreationContext {

  override def serviceCallFactory: ServiceCallFactory =
    ScalaServiceCallFactoryAdapter(javaSdkCreationContext.serviceCallFactory())

  override def getGrpcClient[T](clientClass: Class[T], service: String): T =
    javaSdkCreationContext.getGrpcClient(clientClass, service)

  override def materializer(): Materializer = javaSdkCreationContext.materializer()
}

private[scalasdk] final case class ScalaActionContextAdapter(javaSdkContext: javasdk.action.ActionContext)
    extends ActionContext {
  override def metadata: Metadata =
    // FIXME can we get rid of this cast?
    new MetadataImpl(javaSdkContext.metadata().asInstanceOf[com.akkaserverless.javasdk.impl.MetadataImpl])

  override def eventSubject: Option[String] =
    javaSdkContext.eventSubject().toScala

  override def serviceCallFactory: ServiceCallFactory =
    ScalaServiceCallFactoryAdapter(javaSdkContext.serviceCallFactory())

  override def getGrpcClient[T](clientClass: Class[T], service: String): T =
    javaSdkContext.getGrpcClient(clientClass, service)

  override def materializer(): Materializer = javaSdkContext.materializer()
}
