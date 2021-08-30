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

package com.akkaserverless.javasdk.impl.view

import com.akkaserverless.javasdk.view.{UpdateContext, View}

import java.util.Optional

abstract class ViewHandler[S, V <: View[S]](protected val view: V) {

  final def handleUpdate(state: Option[Any], event: Any, context: UpdateContext): View.UpdateEffect[_] = {
    val stateOrEmpty: S = state match {
      case Some(preExisting) => preExisting.asInstanceOf[S]
      case None => view.emptyState()
    }
    try {
      view.setUpdateContext(Optional.of(context))
      handleUpdate(context.eventName(), stateOrEmpty, event)
    } catch {
      case missing: UpdateHandlerNotFound =>
        throw new ViewException(
          context.viewId,
          missing.eventName,
          "No update handler found for event [" + missing.eventName + "] on " + view.getClass.toString,
          Option.empty
        )
    } finally {
      view.setUpdateContext(Optional.empty())
    }
  }

  def handleUpdate(commandName: String, state: S, event: Any): View.UpdateEffect[S]

}
