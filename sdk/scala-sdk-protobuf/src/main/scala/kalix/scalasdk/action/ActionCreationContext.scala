/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.scalasdk.action

import io.opentelemetry.api.trace.Tracer
import kalix.scalasdk.Context

trait ActionCreationContext extends Context {

  /**
   * Get an Akka gRPC client for the given service name. The same client instance is shared across components in the
   * application. The lifecycle of the client is managed by the SDK and it should not be stopped by user code.
   *
   * @tparam T
   *   The "service" interface generated for the service by Akka gRPC
   * @param clientClass
   *   The class of a gRPC service generated by Akka gRPC
   * @param service
   *   The name of the service to connect to, either a name of another Kalix service or an external service where
   *   connection details are configured under `akka.grpc.client.[service-name]` in `application.conf`.
   */
  def getGrpcClient[T](clientClass: Class[T], service: String): T

  /**
   * Get an OpenTelemetry tracer for the current action. This will allow for building and automatic exporting of spans.
   *
   * @return
   *   A tracer for the current action, if tracing is configured.
   */
  def getTracer: Tracer
}
