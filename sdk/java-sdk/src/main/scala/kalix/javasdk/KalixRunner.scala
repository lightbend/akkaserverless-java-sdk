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

package kalix.javasdk

import java.lang.management.ManagementFactory
import akka.Done
import akka.actor.CoordinatedShutdown.Reason
import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import kalix.javasdk.impl.action.{ ActionService, ActionsImpl }
import kalix.javasdk.impl.replicatedentity.{ ReplicatedEntitiesImpl, ReplicatedEntityService }
import kalix.javasdk.impl.valueentity.{ ValueEntitiesImpl, ValueEntityService }
import kalix.javasdk.impl.eventsourcedentity.{ EventSourcedEntitiesImpl, EventSourcedEntityService }
import kalix.javasdk.impl.{ AbstractContext, DiscoveryImpl, Service }
import kalix.protocol.action.ActionsHandler
import kalix.protocol.discovery.DiscoveryHandler
import kalix.protocol.event_sourced_entity.EventSourcedEntitiesHandler
import kalix.protocol.replicated_entity.ReplicatedEntitiesHandler
import kalix.protocol.value_entity.ValueEntitiesHandler
import com.typesafe.config.{ Config, ConfigFactory }

import java.util.concurrent.CompletionStage
import kalix.javasdk.impl.view.ViewService

import scala.compat.java8.FutureConverters
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import kalix.javasdk.impl.view.ViewsImpl
import kalix.protocol.view.ViewsHandler
import org.slf4j.LoggerFactory

object KalixRunner {
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
 * The KalixRunner is responsible for handle the bootstrap of entities, and is used by [[Kalix#start()]] to set up the
 * local server with the given configuration.
 *
 * KalixRunner can be seen as a low-level API for cases where [[Kalix#start()]] isn't enough.
 */
final class KalixRunner private[this] (
    _system: ActorSystem,
    serviceFactories: Map[String, java.util.function.Function[ActorSystem, Service]],
    sdkName: String) {
  private[kalix] implicit val system: ActorSystem = _system
  private val log = LoggerFactory.getLogger(getClass)

  private[this] final val configuration =
    new KalixRunner.Configuration(system.settings.config.getConfig("kalix"))

  private val services = serviceFactories.toSeq.map { case (serviceName, factory) =>
    serviceName -> factory(system)
  }.toMap

  /**
   * Creates a KalixRunner from the given services. Use the default config to create the internal ActorSystem.
   */
  def this(services: java.util.Map[String, java.util.function.Function[ActorSystem, Service]], sdkName: String) {
    this(
      ActorSystem(
        "kalix", {
          val conf = ConfigFactory.load()
          conf.getConfig("kalix.system").withFallback(conf)
        }),
      services.asScala.toMap,
      sdkName)
  }

  /**
   * Creates a KalixRunner from the given services and config. The config should have the same structure as the
   * reference.conf, with `kalix` as the root section, and the configuration for the internal ActorSystem is in the
   * `kalix.system` section.
   */
  def this(
      services: java.util.Map[String, java.util.function.Function[ActorSystem, Service]],
      config: Config,
      sdkName: String) {
    this(ActorSystem("kalix", config.getConfig("kalix.system").withFallback(config)), services.asScala.toMap, sdkName)
  }

  private val rootContext: Context = new AbstractContext(system) {}

  private[this] def createRoutes(): PartialFunction[HttpRequest, Future[HttpResponse]] = {

    val serviceRoutes =
      services.groupBy(_._2.getClass).foldLeft(PartialFunction.empty[HttpRequest, Future[HttpResponse]]) {

        case (route, (serviceClass, eventSourcedServices: Map[String, EventSourcedEntityService] @unchecked))
            if serviceClass == classOf[EventSourcedEntityService] =>
          val eventSourcedImpl = new EventSourcedEntitiesImpl(system, eventSourcedServices, configuration)
          route.orElse(EventSourcedEntitiesHandler.partial(eventSourcedImpl))

        case (route, (serviceClass, services: Map[String, ReplicatedEntityService] @unchecked))
            if serviceClass == classOf[ReplicatedEntityService] =>
          val replicatedEntitiesImpl = new ReplicatedEntitiesImpl(system, services)
          route.orElse(ReplicatedEntitiesHandler.partial(replicatedEntitiesImpl))

        case (route, (serviceClass, actionServices: Map[String, ActionService] @unchecked))
            if serviceClass == classOf[ActionService] =>
          val actionImpl = new ActionsImpl(system, actionServices, rootContext)
          route.orElse(ActionsHandler.partial(actionImpl))

        case (route, (serviceClass, entityServices: Map[String, ValueEntityService] @unchecked))
            if serviceClass == classOf[ValueEntityService] =>
          val valueEntityImpl = new ValueEntitiesImpl(system, entityServices)
          route.orElse(ValueEntitiesHandler.partial(valueEntityImpl))

        case (route, (serviceClass, viewServices: Map[String, ViewService] @unchecked))
            if serviceClass == classOf[ViewService] =>
          val viewsImpl = new ViewsImpl(system, viewServices, rootContext)
          route.orElse(ViewsHandler.partial(viewsImpl))

        case (_, (serviceClass, _)) =>
          sys.error(s"Unknown service type: $serviceClass")
      }

    val discovery = DiscoveryHandler.partial(new DiscoveryImpl(system, services, sdkName))

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
        CoordinatedShutdown.get(system).run(KalixRunner.BindFailure)
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
