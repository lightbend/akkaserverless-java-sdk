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

package kalix.devtools.impl

import kalix.devtools.BuildInfo
import kalix.devtools.impl.KalixProxyContainer.KalixProxyContainerConfig
import org.slf4j.LoggerFactory
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

object KalixProxyContainer {

  case class KalixProxyContainerConfig(
      proxyImage: String,
      proxyPort: Int,
      userFunctionPort: Int,
      serviceName: String,
      aclEnabled: Boolean,
      viewFeaturesAll: Boolean,
      brokerConfigFile: String,
      pubsubEmulatorPort: Option[Int])

  def apply(config: KalixProxyContainerConfig): KalixProxyContainer = {
    val dockerImage: DockerImageName = DockerImageName.parse(config.proxyImage)
    new KalixProxyContainer(dockerImage, config)
  }

}

class KalixProxyContainer private (image: DockerImageName, config: KalixProxyContainerConfig)
    extends GenericContainer[KalixProxyContainer](image) {

  private val containerLogger = LoggerFactory.getLogger("kalix-proxy-server")
  withLogConsumer(new Slf4jLogConsumer(containerLogger).withSeparateOutputStreams)

  private val defaultConfigDir = "/conf"
  // make sure that the proxy container can access the local file system
  // we will mount the current directory as /conf and use that as the location for the broker config file
  // or any other config files that we might need in the future
  withFileSystemBind(".", defaultConfigDir, BindMode.READ_ONLY)

  private val proxyPort = config.proxyPort
  private val userFunctionPort = config.userFunctionPort
  addFixedExposedPort(proxyPort, proxyPort)

  withEnv("HTTP_PORT", String.valueOf(proxyPort))
  withEnv("USER_FUNCTION_HOST", "host.testcontainers.internal")
  withEnv("USER_FUNCTION_PORT", String.valueOf(userFunctionPort))

  withEnv("ACL_ENABLED", config.aclEnabled.toString)
  withEnv("VIEW_FEATURES_ALL", config.viewFeaturesAll.toString)

  if (config.serviceName.nonEmpty) {
    withEnv("SERVICE_NAME", config.serviceName)

    // use service name as container instance name (instead of random one from testcontainers)
    withCreateContainerCmdModifier(cmd => cmd.withName(config.serviceName))
  }

  if (config.brokerConfigFile.nonEmpty)
    withEnv("BROKER_CONFIG_FILE", defaultConfigDir + "/" + config.brokerConfigFile)

  // JVM are that should be passed to the proxy container on start-up
  val containerArgs =
    Seq("-Dconfig.resource=dev-mode.conf", "-Dlogback.configurationFile=logback-dev-mode.xml")

  val pubSubContainerArg =
    config.pubsubEmulatorPort.map { port =>
      withEnv("PUBSUB_EMULATOR_HOST", "host.testcontainers.internal")
      "-Dkalix.proxy.eventing.support=google-pubsub-emulator"
    }

  val finalArgs = containerArgs ++ pubSubContainerArg
  withCommand(finalArgs: _*)

  @volatile
  private var started: Boolean = false

  override def start(): Unit = {
    containerLogger.info("Starting Kalix Proxy Server container...")
    containerLogger.info("Using proxy image [{}]", image)
    containerLogger.info("KalixProxyContainer config : {}", config)

    Testcontainers.exposeHostPorts(userFunctionPort)
    config.pubsubEmulatorPort.foreach(Testcontainers.exposeHostPorts(_))

    super.start()
    started = true
  }

  override def stop(): Unit =
    if (started) {
      containerLogger.info("Stopping Kalix Proxy Server...")
      super.stop()
    }
}
