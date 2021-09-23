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

package com.akkaserverless.javasdk.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.akkaserverless.javasdk.Context
import com.akkaserverless.javasdk.ServiceCallFactory

/**
 * INTERNAL API
 */
private[impl] trait ActivatableContext extends Context {
  private final var active = true
  final def deactivate(): Unit = active = false
  final def checkActive(): Unit = if (!active) throw new IllegalStateException("Context no longer active!")
}

/**
 * INTERNAL API
 */
private[javasdk] abstract class AbstractContext(
    override val serviceCallFactory: ServiceCallFactory,
    system: ActorSystem)
    extends Context {
  override def getGrpcClient[T](clientClass: Class[T], service: String): T =
    GrpcClients(system).getGrpcClient(clientClass, service)

  override def materializer(): Materializer = Materializer(system)
}
