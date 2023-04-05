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

package kalix.javasdk.testkit;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import kalix.javasdk.Kalix;
import kalix.javasdk.KalixRunner;
import kalix.javasdk.Principal;
import kalix.javasdk.impl.GrpcClients;
import kalix.javasdk.impl.ProxyInfoHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Testkit for running Kalix services locally.
 *
 * <p>Requires Docker for starting a local instance of the Kalix proxy.
 *
 * <p>Create a KalixTestkit with an {@link Kalix} service descriptor, and then {@link #start} the
 * testkit before testing the service with gRPC or HTTP clients. Call {@link #stop} after tests are
 * complete.
 */
public class KalixTestKit {

  /** Settings for KalixTestkit. */
  public static class Settings {
    /** Default stop timeout (10 seconds). */
    public static Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(10);
    /** Default settings for KalixTestkit. */
    public static Settings DEFAULT = new Settings(DEFAULT_STOP_TIMEOUT);

    /** Timeout setting for stopping the local Kalix test instance. */
    public final Duration stopTimeout;

    /** The name of this service when deployed. */
    public final String serviceName;

    /** Whether ACL checking is enabled. */
    public final boolean aclEnabled;

    /** Whether advanced View features are enabled. */
    public final boolean advancedViews;

    /** To override workflow tick interval for integration tests */
    public final Optional<Duration> workflowTickInterval;

    /**
     * Create new settings for KalixTestkit.
     *
     * @param stopTimeout timeout to use when waiting for Kalix to stop
     * @deprecated Use Settings.DEFAULT.withStopTimeout() instead.
     */
    @Deprecated
    public Settings(final Duration stopTimeout) {
      this(stopTimeout, "self", false, false, Optional.empty());
    }

    private Settings(
        final Duration stopTimeout,
        final String serviceName,
        final boolean aclEnabled,
        final boolean advancedViews,
        final Optional<Duration> workflowTickInterval) {
      this.stopTimeout = stopTimeout;
      this.serviceName = serviceName;
      this.aclEnabled = aclEnabled;
      this.advancedViews = advancedViews;
      this.workflowTickInterval = workflowTickInterval;
    }

    /**
     * Set a custom stop timeout, for stopping the local Kalix test instance.
     *
     * @param stopTimeout timeout to use when waiting for Kalix to stop
     * @return updated Settings
     */
    public Settings withStopTimeout(final Duration stopTimeout) {
      return new Settings(stopTimeout, serviceName, aclEnabled, advancedViews, workflowTickInterval);
    }

    /**
     * Set the name of this service. This will be used by the service when making calls on other
     * services run by the testkit to authenticate itself, allowing those services to apply ACLs
     * based on that name.
     *
     * @param serviceName The name of this service.
     * @return The updated settings.
     */
    public Settings withServiceName(final String serviceName) {
      return new Settings(stopTimeout, serviceName, aclEnabled, advancedViews, workflowTickInterval);
    }

    /**
     * Disable ACL checking in this service.
     *
     * @return The updated settings.
     */
    public Settings withAclDisabled() {
      return new Settings(stopTimeout, serviceName, false, advancedViews, workflowTickInterval);
    }

    /**
     * Enable ACL checking in this service.
     *
     * @return The updated settings.
     */
    public Settings withAclEnabled() {
      return new Settings(stopTimeout, serviceName, true, advancedViews, workflowTickInterval);
    }

    /**
     * Enable advanced View features for this service.
     *
     * @return The updated settings.
     */
    public Settings withAdvancedViews() {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, workflowTickInterval);
    }

    /**
     * Overrides workflow tick interval
     *
     * @return The updated settings.
     */
    public Settings withWorkflowTickInterval(Duration tickInterval) {
      return new Settings(stopTimeout, serviceName, aclEnabled, true, Optional.of(tickInterval));
    }

    @Override
    public String toString() {
      return "Settings(" +
          "stopTimeout=" + stopTimeout +
          ", serviceName='" + serviceName + '\'' +
          ", aclEnabled=" + aclEnabled +
          ", advancedViews=" + advancedViews +
          ", workflowTickInterval=" + workflowTickInterval +
          ')';
    }
  }

  private static final Logger log = LoggerFactory.getLogger(KalixTestKit.class);

  private final Kalix kalix;
  private final Settings settings;

  private boolean started = false;
  private String proxyHost;
  private int proxyPort;
  private Optional<KalixProxyContainer> proxyContainer;
  private KalixRunner runner;
  private ActorSystem testSystem;

  /**
   * Create a new testkit for a Kalix service descriptor.
   *
   * @param kalix Kalix service descriptor
   */
  public KalixTestKit(final Kalix kalix) {
    this(kalix, Settings.DEFAULT);
  }

  /**
   * Create a new testkit for a Kalix service descriptor with custom settings.
   *
   * @param kalix Kalix service descriptor
   * @param settings custom testkit settings
   */
  public KalixTestKit(final Kalix kalix, final Settings settings) {
    this.kalix = kalix;
    this.settings = settings;
  }

  /**
   * Start this testkit with default configuration (loaded from {@code application.conf}).
   *
   * @return this KalixTestkit
   */
  public KalixTestKit start() {
    return start(ConfigFactory.load());
  }

  /**
   * Start this testkit with custom configuration (overrides {@code application.conf}).
   *
   * @param config custom test configuration for the KalixRunner
   * @return this KalixTestkit
   */
  public KalixTestKit start(final Config config) {
    if (started) {throw new IllegalStateException("KalixTestkit already started");}

    Boolean useTestContainers = Optional.ofNullable(System.getenv("KALIX_TESTKIT_USE_TEST_CONTAINERS")).map(Boolean::valueOf).orElse(true);
    int port = userFunctionPort(useTestContainers);
    Map<String, Object> conf = new HashMap<>();
    conf.put("kalix.user-function-port", port);
    // don't kill the test JVM when terminating the KalixRunner
    conf.put("kalix.system.akka.coordinated-shutdown.exit-jvm", "off");
    Config testConfig = ConfigFactory.parseMap(conf);

    runner = kalix.createRunner(testConfig.withFallback(config));
    runner.run();

    testSystem = ActorSystem.create("KalixTestkit");

    runProxy(useTestContainers, port);

    started = true;

    if (log.isDebugEnabled()) {log.debug("TestKit using [{}:{}] for calls to proxy from service", proxyHost, proxyPort);}
    return this;
  }

  private void runProxy(Boolean useTestContainers, int port) {

    if (useTestContainers) {
      var proxyContainer = new KalixProxyContainer(port);
      this.proxyContainer = Optional.of(proxyContainer);
      proxyContainer.addEnv("SERVICE_NAME", settings.serviceName);
      proxyContainer.addEnv("ACL_ENABLED", Boolean.toString(settings.aclEnabled));
      proxyContainer.addEnv("VIEW_FEATURES_ALL", Boolean.toString(settings.advancedViews));
      proxyContainer.addEnv("JAVA_TOOL_OPTIONS", "-Dlogback.configurationFile=logback-dev-mode.xml");
      settings.workflowTickInterval.ifPresent(tickInterval -> proxyContainer.addEnv("WORKFLOW_TICK_INTERVAL", tickInterval.toMillis() + ".millis"));

      proxyContainer.start();

      proxyPort = proxyContainer.getProxyPort();
      proxyHost = proxyContainer.getHost();

    } else {
      proxyPort = 9000;
      proxyHost = "localhost";

      Http http = Http.get(testSystem);
      log.info("Checking kalix-proxy status");
      CompletionStage<String> checkingProxyStatus = Patterns.retry(() -> http.singleRequest(HttpRequest.GET("http://localhost:8558/ready")).thenCompose(response -> {
        int responseCode = response.status().intValue();
        if (responseCode == 200) {
          log.info("Kalix-proxy started");
          return CompletableFuture.completedStage("Ok");
        } else {
          log.info("Waiting for kalix-proxy, current response code is {}", responseCode);
          return CompletableFuture.failedFuture(new IllegalStateException("Proxy not started."));
        }
      }), 10, Duration.ofSeconds(1), testSystem);

      try {
        checkingProxyStatus.toCompletableFuture().get();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
    // the proxy will announce its host and default port, but to communicate with it,
    // we need the use the port and host that testcontainers will expose
    // therefore, we set a port override in ProxyInfoHolder to allow for inter-component communication
    ProxyInfoHolder holder = ProxyInfoHolder.get(runner.system());
    holder.overridePort(proxyPort);
    holder.overrideProxyHost(proxyHost);
  }

  private int userFunctionPort(Boolean useTestContainers) {
    if (useTestContainers) {
      return availableLocalPort();
    } else {
      return KalixProxyContainer.DEFAULT_USER_FUNCTION_PORT;
    }
  }

  /**
   * Get the host name/IP address where the Kalix service is available. This is relevant in certain
   * Continuous Integration environments.
   *
   * @return Kalix host
   */
  public String getHost() {
    if (!started) {throw new IllegalStateException("Need to start KalixTestkit before accessing the host name");}
    return proxyHost;
  }

  /**
   * Get the local port where the Kalix service is available.
   *
   * @return local Kalix port
   */
  public int getPort() {
    if (!started) {throw new IllegalStateException("Need to start KalixTestkit before accessing the port");}
    return proxyPort;
  }

  /**
   * Get an Akka gRPC client for the given service name. The same client instance is shared for the
   * test. The lifecycle of the client is managed by the SDK and it should not be stopped by user
   * code.
   *
   * @param <T> The "service" interface generated for the service by Akka gRPC
   * @param clientClass The class of a gRPC service generated by Akka gRPC
   */
  public <T> T getGrpcClient(Class<T> clientClass) {
    return GrpcClients.get(getActorSystem()).getGrpcClient(clientClass, getHost(), getPort());
  }

  /**
   * Get an Akka gRPC client for the given service name, authenticating using the given principal.
   * The same client instance is shared for the test. The lifecycle of the client is managed by the
   * SDK and it should not be stopped by user code.
   *
   * @param <T> The "service" interface generated for the service by Akka gRPC
   * @param clientClass The class of a gRPC service generated by Akka gRPC
   * @param principal The principal to authenticate calls to the service as.
   */
  public <T> T getGrpcClientForPrincipal(Class<T> clientClass, Principal principal) {
    String serviceName = null;
    if (principal == Principal.SELF) {
      serviceName = settings.serviceName;
    } else if (principal instanceof Principal.LocalService) {
      serviceName = ((Principal.LocalService) principal).getName();
    }
    if (serviceName != null) {
      return GrpcClients.get(getActorSystem())
          .getGrpcClient(clientClass, getHost(), getPort(), serviceName);
    } else {
      return GrpcClients.get(getActorSystem()).getGrpcClient(clientClass, getHost(), getPort());
    }
  }

  /**
   * An Akka Stream materializer to use for running streams. Needed for example in a command handler
   * which accepts streaming elements but returns a single async reply once all streamed elements
   * has been consumed.
   */
  public Materializer getMaterializer() {
    return SystemMaterializer.get(getActorSystem()).materializer();
  }

  /**
   * Get an {@link ActorSystem} for creating Akka HTTP clients.
   *
   * @return test actor system
   */
  public ActorSystem getActorSystem() {
    if (!started)
      throw new IllegalStateException("Need to start KalixTestkit before accessing actor system");
    return testSystem;
  }

  /**
   * Get {@link GrpcClientSettings} for creating Akka gRPC clients.
   *
   * @return test gRPC client settings
   * @deprecated Use <code>getGrpcClient</code> instead.
   */
  @Deprecated(since = "0.8.1", forRemoval = true)
  public GrpcClientSettings getGrpcClientSettings() {
    if (!started)
      throw new IllegalStateException(
          "Need to start KalixTestkit before accessing gRPC client settings");
    return GrpcClientSettings.connectToServiceAt(getHost(), getPort(), testSystem).withTls(false);
  }

  /** Stop the testkit and local Kalix. */
  public void stop() {
    try {
      proxyContainer.ifPresent(container -> container.stop());
    } catch (Exception e) {
      log.error("KalixTestkit proxy container failed to stop", e);
    }
    try {
      testSystem.terminate();
      testSystem
          .getWhenTerminated()
          .toCompletableFuture()
          .get(settings.stopTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      log.error("KalixTestkit ActorSystem failed to terminate", e);
    }
    try {
      runner
          .terminate()
          .toCompletableFuture()
          .get(settings.stopTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      log.error("KalixTestkit KalixRunner failed to terminate", e);
    }
    started = false;
  }

  /**
   * Get an available local port for testing.
   *
   * @return available local port
   */
  public static int availableLocalPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Couldn't get available local port", e);
    }
  }
}
