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

package com.akkaserverless.scalasdk.action

import scala.collection.immutable

import com.akkaserverless.scalasdk.impl.ComponentOptions

object ActionOptions {

  def defaults: ActionOptions = ActionOptionsImpl(Set.empty)

  private[akkaserverless] final case class ActionOptionsImpl(forwardHeaders: immutable.Set[String])
      extends ActionOptions {

    /**
     * Ask Akka Serverless to forward these headers from the incoming request as metadata headers for the incoming
     * commands. By default no headers except "X-Server-Timing" are forwarded.
     */
    override def withForwardHeaders(headers: immutable.Set[String]): ActionOptions =
      copy(forwardHeaders = headers)
  }
}
trait ActionOptions extends ComponentOptions
