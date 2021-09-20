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

package com.akkaserverless.javasdk

import java.lang.management.ManagementFactory
import akka.Done
import akka.actor.CoordinatedShutdown.Reason
import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import com.akkaserverless.javasdk.impl.action.{ ActionService, ActionsImpl }
import com.akkaserverless.javasdk.impl.replicatedentity.{ ReplicatedEntitiesImpl, ReplicatedEntityService }
import com.akkaserverless.javasdk.impl.valueentity.{ ValueEntitiesImpl, ValueEntityService }
import com.akkaserverless.javasdk.impl.eventsourcedentity.{ EventSourcedEntitiesImpl, EventSourcedEntityService }
import com.akkaserverless.javasdk.impl.{
  ComponentOptions,
  DiscoveryImpl,
  ResolvedServiceCallFactory,
  ResolvedServiceMethod,
  Service
}
import com.akkaserverless.protocol.action.ActionsHandler
import com.akkaserverless.protocol.discovery.DiscoveryHandler
import com.akkaserverless.protocol.event_sourced_entity.EventSourcedEntitiesHandler
import com.akkaserverless.protocol.replicated_entity.ReplicatedEntitiesHandler
import com.akkaserverless.protocol.value_entity.ValueEntitiesHandler
import com.google.protobuf.Descriptors
import com.typesafe.config.{ Config, ConfigFactory }

import java.util.concurrent.CompletionStage
import com.akkaserverless.javasdk.impl.view.ViewService
import com.akkaserverless.javasdk.impl.AbstractContext

import scala.compat.java8.FutureConverters
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import com.akkaserverless.javasdk.impl.view.ViewsImpl
import com.akkaserverless.protocol.view.ViewsHandler
import org.slf4j.LoggerFactory

object AkkaServerlessRunner {
  object BindFailure extends Reason

  final case class Configuration(userFunctionInterface: String, userFunctionPort: Int, snapshotEvery: Int) {
    validate()
    def this(config: Config) = {
      this(
        userFunctionInterface = config.getString("user-function-interface"),
        userFunctionPort = config.getInt("user-function-port"),
        snapshotEvery = config.getInt("event-sourced-entity.snapshot-every"))
    }

    private def validate(): Unit = {
      require(userFunctionInterface.length > 0, s"user-function-interface must not be empty")
      require(userFunctionPort > 0, s"user-function-port must be greater than 0")
    }
  }
}

/**
 * The AkkaServerlessRunner is responsible for handle the bootstrap of entities, and is used by
 * [[com.akkaserverless.javasdk.AkkaServerless#start()]] to set up the local server with the given configuration.
 *
 * AkkaServerlessRunner can be seen as a low-level API for cases where
 * [[com.akkaserverless.javasdk.AkkaServerless#start()]] isn't enough.
 */
final class AkkaServerlessRunner private[this] (
    _system: ActorSystem,
    serviceFactories: Map[String, java.util.function.Function[ActorSystem, Service]]) {
  private[akkaserverless] implicit val system: ActorSystem = _system
  private val log = LoggerFactory.getLogger(getClass)

  private[this] final val configuration =
    new AkkaServerlessRunner.Configuration(system.settings.config.getConfig("akkaserverless"))

  private val services = serviceFactories.toSeq.map { case (serviceName, factory) =>
    serviceName -> factory(system)
  }.toMap

  /**
   * Creates an AkkaServerlessRunner from the given services. Use the default config to create the internal ActorSystem.
   */
  def this(services: java.util.Map[String, java.util.function.Function[ActorSystem, Service]]) {
    this(
      ActorSystem(
        "akkaserverless", {
          val conf = ConfigFactory.load()
          conf.getConfig("akkaserverless.system").withFallback(conf)
        }),
      services.asScala.toMap)
  }

  /**
   * Creates an AkkaServerlessRunner from the given services and config. The config should have the same structure as
   * the reference.conf, with `akkaserverless` as the root section, and the configuration for the internal ActorSystem
   * is in the `akkaserverless.system` section.
   */
  def this(services: java.util.Map[String, java.util.function.Function[ActorSystem, Service]], config: Config) {
    this(
      ActorSystem("akkaserverless", config.getConfig("akkaserverless.system").withFallback(config)),
      services.asScala.toMap)
  }

  private val rootContext: Context = new AbstractContext(new ResolvedServiceCallFactory(services), system) {}

  private[this] def createRoutes(): PartialFunction[HttpRequest, Future[HttpResponse]] = {

    val serviceRoutes =
      services.groupBy(_._2.getClass).foldLeft(PartialFunction.empty[HttpRequest, Future[HttpResponse]]) {

        case (route, (serviceClass, eventSourcedServices: Map[String, EventSourcedEntityService] @unchecked))
            if serviceClass == classOf[EventSourcedEntityService] =>
          val eventSourcedImpl = new EventSourcedEntitiesImpl(system, eventSourcedServices, rootContext, configuration)
          route.orElse(EventSourcedEntitiesHandler.partial(eventSourcedImpl))

        case (route, (serviceClass, services: Map[String, ReplicatedEntityService] @unchecked))
            if serviceClass == classOf[ReplicatedEntityService] =>
          val replicatedEntitiesImpl = new ReplicatedEntitiesImpl(system, services, rootContext)
          route.orElse(ReplicatedEntitiesHandler.partial(replicatedEntitiesImpl))

        case (route, (serviceClass, actionServices: Map[String, ActionService] @unchecked))
            if serviceClass == classOf[ActionService] =>
          val actionImpl = new ActionsImpl(system, actionServices, rootContext)
          route.orElse(ActionsHandler.partial(actionImpl))

        case (route, (serviceClass, entityServices: Map[String, ValueEntityService] @unchecked))
            if serviceClass == classOf[ValueEntityService] =>
          val valueEntityImpl = new ValueEntitiesImpl(system, entityServices, rootContext, configuration)
          route.orElse(ValueEntitiesHandler.partial(valueEntityImpl))

        case (route, (serviceClass, viewServices: Map[String, ViewService] @unchecked))
            if serviceClass == classOf[ViewService] =>
          val viewsImpl = new ViewsImpl(system, viewServices, rootContext)
          route.orElse(ViewsHandler.partial(viewsImpl))

        case (_, (serviceClass, _)) =>
          sys.error(s"Unknown service type: $serviceClass")
      }

    val discovery = DiscoveryHandler.partial(new DiscoveryImpl(system, services))

    serviceRoutes.orElse(discovery).orElse { case _ => Future.successful(HttpResponse(StatusCodes.NotFound)) }
  }

  /**
   * Starts a server with the configured entities.
   *
   * @return
   *   a CompletionStage which will be completed when the server has shut down.
   */
  def run(): CompletionStage[Done] = {
    import scala.concurrent.duration._
    import system.dispatcher

    logJvmInfo()

    val bound = Http
      .get(system)
      .newServerAt(configuration.userFunctionInterface, configuration.userFunctionPort)
      .bind(createRoutes())
      // note that DiscoveryImpl will add a task in PhaseBeforeServiceUnbind to wait
      // for proxy termination
      .map(_.addToCoordinatedShutdown(3.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.debug("gRPC server started {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error(
          "Failed to bind gRPC server {}:{}, terminating system. {}",
          configuration.userFunctionInterface,
          configuration.userFunctionPort,
          ex)
        CoordinatedShutdown.get(system).run(AkkaServerlessRunner.BindFailure)
    }

    // Complete the returned CompletionStage with bind failure or Done when system is terminated
    FutureConverters.toJava(bound).thenCompose(_ => system.getWhenTerminated).thenApply(_ => Done)
  }

  /**
   * Terminates the server.
   *
   * @return
   *   a CompletionStage which will be completed when the server has shut down.
   */
  def terminate(): CompletionStage[Done] =
    FutureConverters.toJava(system.terminate()).thenApply(_ => Done)

  private def logJvmInfo(): Unit = {
    val osMBean = ManagementFactory.getOperatingSystemMXBean
    val memoryMBean = ManagementFactory.getMemoryMXBean
    val heap = memoryMBean.getHeapMemoryUsage
    val jvmName = sys.props.get("java.runtime.name").orElse(sys.props.get("java.vm.name")).getOrElse("")
    val jvmVersion = sys.props.get("java.runtime.version").orElse(sys.props.get("java.vm.version")).getOrElse("")

    log.debug(
      "JVM [{} {}], max heap [{} MB], processors [{}]",
      jvmName,
      jvmVersion,
      heap.getMax / 1024 / 1024,
      osMBean.getAvailableProcessors)
  }
}
