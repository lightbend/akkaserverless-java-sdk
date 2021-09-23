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

package com.akkaserverless.scalasdk.impl.valueentity

import akka.stream.Materializer

import java.util.Optional
import scala.collection.immutable
import scala.compat.java8.DurationConverters._
import scala.jdk.CollectionConverters.SetHasAsJava
import scala.jdk.CollectionConverters.SetHasAsScala
import scala.jdk.OptionConverters._
import com.akkaserverless.javasdk
import com.akkaserverless.scalasdk.PassivationStrategy
import com.akkaserverless.scalasdk.ServiceCallFactory
import com.akkaserverless.scalasdk.impl.MetadataImpl
import com.akkaserverless.scalasdk.impl.ScalaServiceCallFactoryAdapter
import com.akkaserverless.scalasdk.valueentity.CommandContext
import com.akkaserverless.scalasdk.valueentity.ValueEntity
import com.akkaserverless.scalasdk.valueentity.ValueEntityContext
import com.akkaserverless.scalasdk.valueentity.ValueEntityOptions
import com.akkaserverless.scalasdk.valueentity.ValueEntityProvider
import com.google.protobuf.Descriptors

private[scalasdk] final class JavaValueEntityAdapter[S](scalasdkValueEntity: ValueEntity[S])
    extends javasdk.valueentity.ValueEntity[S] {
  override def emptyState(): S = scalasdkValueEntity.emptyState
  override def _internalSetCommandContext(context: Optional[javasdk.valueentity.CommandContext]): Unit =
    scalasdkValueEntity._internalSetCommandContext(context.map(new JavaCommandContextAdapter(_)).toScala)
}

private[scalasdk] final class JavaValueEntityProviderAdapter[S, E <: ValueEntity[S]](
    scalasdkProvider: ValueEntityProvider[S, E])
    extends javasdk.valueentity.ValueEntityProvider[S, javasdk.valueentity.ValueEntity[S]] {
  override def additionalDescriptors(): Array[Descriptors.FileDescriptor] =
    scalasdkProvider.additionalDescriptors.toArray
  override def entityType(): String = scalasdkProvider.entityType
  override def newHandler(context: javasdk.valueentity.ValueEntityContext)
      : javasdk.impl.valueentity.ValueEntityHandler[S, javasdk.valueentity.ValueEntity[S]] = {
    val scaladslHandler = scalasdkProvider
      .newHandler(new ScalaValueEntityContextAdapter(context))
      .asInstanceOf[ValueEntityHandler[S, ValueEntity[S]]]
    new JavaValueEntityHandlerAdapter[S](new JavaValueEntityAdapter[S](scaladslHandler.entity), scaladslHandler)
  }
  override def options(): javasdk.valueentity.ValueEntityOptions = new JavaValueEntityOptionsAdapter(
    scalasdkProvider.options)
  override def serviceDescriptor(): Descriptors.ServiceDescriptor = scalasdkProvider.serviceDescriptor
}

private[scalasdk] final class JavaValueEntityHandlerAdapter[S](
    javasdkValueEntity: javasdk.valueentity.ValueEntity[S],
    scalasdkHandler: ValueEntityHandler[S, ValueEntity[S]])
    extends javasdk.impl.valueentity.ValueEntityHandler[S, javasdk.valueentity.ValueEntity[S]](javasdkValueEntity) {

  override def handleCommand(
      commandName: String,
      state: S,
      command: Any,
      context: javasdk.valueentity.CommandContext): javasdk.valueentity.ValueEntity.Effect[_] = {
    scalasdkHandler.handleCommand(commandName, state, command, new JavaCommandContextAdapter(context)) match {
      case ValueEntityEffectImpl(javasdkEffectImpl) => javasdkEffectImpl
    }
  }
}
private[scalasdk] final class JavaValueEntityOptionsAdapter(scalasdkValueEntityOptions: ValueEntityOptions)
    extends javasdk.valueentity.ValueEntityOptions {

  def forwardHeaders(): java.util.Set[String] = scalasdkValueEntityOptions.forwardHeaders.asJava

  def withForwardHeaders(headers: java.util.Set[String]): javasdk.valueentity.ValueEntityOptions =
    new JavaValueEntityOptionsAdapter(
      scalasdkValueEntityOptions.withForwardHeaders(immutable.Set.from(headers.asScala)))

  def passivationStrategy(): javasdk.PassivationStrategy =
    new JavaPassivationStrategyAdapter(scalasdkValueEntityOptions.passivationStrategy)

  def withPassivationStrategy(
      passivationStrategy: javasdk.PassivationStrategy): javasdk.valueentity.ValueEntityOptions =
    new JavaValueEntityOptionsAdapter(scalasdkValueEntityOptions.withPassivationStrategy(passivationStrategy))
}

private[scalasdk] final class JavaCommandContextAdapter(val javasdkContext: javasdk.valueentity.CommandContext)
    extends CommandContext {
  override def commandName: String = javasdkContext.commandName()

  override def commandId: Long = javasdkContext.commandId()
  override def getGrpcClient[T](clientClass: Class[T], service: String): T =
    javasdkContext.getGrpcClient(clientClass, service)
  override def serviceCallFactory: ServiceCallFactory =
    ScalaServiceCallFactoryAdapter(javasdkContext.serviceCallFactory())

  override def entityId: String = javasdkContext.entityId()

  override def metadata: com.akkaserverless.scalasdk.Metadata =
    // FIXME can we get rid of this cast?
    new MetadataImpl(javasdkContext.metadata().asInstanceOf[com.akkaserverless.javasdk.impl.MetadataImpl])

  override def materializer(): Materializer = javasdkContext.materializer()
}

private[scalasdk] final class ScalaValueEntityContextAdapter(javasdkContext: javasdk.valueentity.ValueEntityContext)
    extends ValueEntityContext {
  def entityId: String = javasdkContext.entityId()
  override def getGrpcClient[T](clientClass: Class[T], service: String): T =
    javasdkContext.getGrpcClient(clientClass, service)
  override def serviceCallFactory: ServiceCallFactory =
    ScalaServiceCallFactoryAdapter(javasdkContext.serviceCallFactory())

  override def materializer(): Materializer = javasdkContext.materializer()
}

private[scalasdk] class JavaPassivationStrategyAdapter(scalasdkPassivationStrategy: PassivationStrategy)
    extends javasdk.PassivationStrategy {
  def defaultTimeout() = scalasdkPassivationStrategy.defaultTimeout
  def timeout(duration: java.time.Duration) = scalasdkPassivationStrategy.timeout(duration.toScala)
}
