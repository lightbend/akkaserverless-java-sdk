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

package com.akkaserverless.scalasdk

import com.google.protobuf.any.{ Any => ScalaPbAny }

import scala.concurrent.Future

/**
 * Represents a call to a component service that has not yet happened, but will be handed to Akka Serverless for
 * execution. Used with forwards and side effects.
 *
 * @tparam T
 *   the message type of the parameter for the call
 * @tparam R
 *   the message type that the call returns
 *
 * Not for user extension.
 */
trait DeferredCall[T, R] {

  /**
   * The reference to the call.
   *
   * @return
   *   The reference to the call.
   */
  def ref: DeferredCallRef[T, R]

  /**
   * The message to pass to the call when the call is invoked.
   *
   * @return
   *   The message to pass to the call, serialized as an {{{ScalaPbAny}}}
   */
  def message: ScalaPbAny

  /**
   * The metadata to pass with the message when the call is invoked.
   *
   * @return
   *   The metadata.
   */
  def metadata: Metadata

  /**
   * Execute this call right away and get the async result back for composition.
   */
  def execute(): Future[R]
}
