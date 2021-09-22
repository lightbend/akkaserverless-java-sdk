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

import scala.collection.immutable

import com.akkaserverless.scalasdk.impl.ComponentOptions

/** Options used for configuring an entity. */
trait EntityOptions extends ComponentOptions {

  /** @return the passivation strategy for an entity */
  def passivationStrategy: PassivationStrategy

  /**
   * Create an entity option with the given passivation strategy.
   *
   * @param strategy
   *   to be used
   * @return
   *   the entity option
   */
  def withPassivationStrategy(strategy: PassivationStrategy): EntityOptions

  /**
   * @return
   *   the headers requested to be forwarded as metadata (cannot be mutated, use withForwardHeaders)
   */
  def forwardHeaders: immutable.Set[String]

  /**
   * Ask Akka Serverless to forward these headers from the incoming request as metadata headers for the incoming
   * commands. By default no headers except "X-Server-Timing" are forwarded.
   */
  def withForwardHeaders(headers: immutable.Set[String]): EntityOptions
}
