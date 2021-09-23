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

package com.akkaserverless.scalasdk.impl.view

import akka.stream.Materializer

import java.util.Optional
import scala.jdk.CollectionConverters.SetHasAsJava
import scala.jdk.OptionConverters._
import com.akkaserverless.javasdk
import com.akkaserverless.javasdk.view.ViewOptions
import com.akkaserverless.scalasdk.Metadata
import com.akkaserverless.scalasdk.ServiceCallFactory
import com.akkaserverless.scalasdk.impl.MetadataImpl
import com.akkaserverless.scalasdk.impl.ScalaServiceCallFactoryAdapter
import com.akkaserverless.scalasdk.view.UpdateContext
import com.akkaserverless.scalasdk.view.View
import com.akkaserverless.scalasdk.view.ViewCreationContext
import com.akkaserverless.scalasdk.view.ViewProvider
import com.google.protobuf.Descriptors

private[scalasdk] final class Scala2JavaViewAdapter[S](scalasdkView: View[S]) extends javasdk.view.View[S] {
  override def emptyState(): S = scalasdkView.emptyState

  override def _internalSetUpdateContext(context: Optional[javasdk.view.UpdateContext]): Unit =
    scalasdkView._internalSetUpdateContext(context.map(new Java2ScalaUpdateContextAdapter(_)).toScala)
}

private[scalasdk] final class Scala2JavaViewProviderAdapter[S, V <: View[S]](scalasdkProvider: ViewProvider[S, V])
    extends javasdk.view.ViewProvider[S, javasdk.view.View[S]] {
  override def serviceDescriptor(): Descriptors.ServiceDescriptor =
    scalasdkProvider.serviceDescriptor

  override def viewId(): String =
    scalasdkProvider.viewId

  override def options(): ViewOptions =
    javasdk.impl.view.ViewOptionsImpl(scalasdkProvider.options.forwardHeaders.asJava)

  override def newHandler(
      context: javasdk.view.ViewCreationContext): javasdk.impl.view.ViewHandler[S, javasdk.view.View[S]] = {
    val scaladslHandler = scalasdkProvider
      .newHandler(new Java2ScalaViewCreationContextAdapter(context))
      .asInstanceOf[ViewHandler[S, View[S]]]
    new Scala2JavaViewHandlerAdapter[S](new Scala2JavaViewAdapter[S](scaladslHandler.view), scaladslHandler)
  }

  override def additionalDescriptors(): Array[Descriptors.FileDescriptor] =
    scalasdkProvider.additionalDescriptors.toArray
}

private[scalasdk] class Scala2JavaViewHandlerAdapter[S](
    javasdkView: javasdk.view.View[S],
    scalasdkHandler: ViewHandler[S, View[S]])
    extends javasdk.impl.view.ViewHandler[S, javasdk.view.View[S]](javasdkView) {

  override def handleUpdate(commandName: String, state: S, event: Any): javasdk.view.View.UpdateEffect[S] = {
    scalasdkHandler.handleUpdate(commandName, state, event) match {
      case effect: ViewUpdateEffectImpl.PrimaryUpdateEffect[S] => effect.toJavaSdk
    }
  }
}

private[scalasdk] final class Java2ScalaViewCreationContextAdapter(javasdkContext: javasdk.view.ViewCreationContext)
    extends ViewCreationContext {
  override def viewId: String =
    javasdkContext.viewId()

  override def serviceCallFactory: ServiceCallFactory =
    ScalaServiceCallFactoryAdapter(javasdkContext.serviceCallFactory())

  override def getGrpcClient[T](clientClass: Class[T], service: String): T =
    javasdkContext.getGrpcClient(clientClass, service)

  override def materializer(): Materializer = javasdkContext.materializer()
}

private[scalasdk] final class Java2ScalaUpdateContextAdapter(val javasdkContext: javasdk.view.UpdateContext)
    extends UpdateContext {
  override def eventSubject: Option[String] =
    javasdkContext.eventSubject().toScala

  override def eventName: String =
    javasdkContext.eventName()

  override def serviceCallFactory: ServiceCallFactory =
    ScalaServiceCallFactoryAdapter(javasdkContext.serviceCallFactory())

  override def metadata: Metadata =
    // FIXME can we get rid of this cast?
    new MetadataImpl(javasdkContext.metadata().asInstanceOf[com.akkaserverless.javasdk.impl.MetadataImpl])

  override def viewId: String =
    javasdkContext.viewId()

  override def getGrpcClient[T](clientClass: Class[T], service: String): T =
    javasdkContext.getGrpcClient(clientClass, service)

  override def materializer(): Materializer = javasdkContext.materializer()
}
