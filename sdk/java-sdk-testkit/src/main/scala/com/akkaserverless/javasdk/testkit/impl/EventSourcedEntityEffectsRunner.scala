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

package com.akkaserverless.javasdk.testkit.impl

import com.akkaserverless.javasdk.eventsourcedentity.CommandContext
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity
import com.akkaserverless.javasdk.testkit.EventSourcedResult
import com.akkaserverless.javasdk.testkit.impl.EventSourcedResultImpl
import java.util.Optional
import java.util.{ List => JList }
import java.util.ArrayList
import scala.jdk.CollectionConverters._

/** Extended by generated code, not meant for user extension */
abstract class EventSourcedEntityEffectsRunner[S](entity: EventSourcedEntity[S]) {
  private var _state: S = entity.emptyState
  private var events: JList[Any] = new ArrayList()
  //FIXME can't use protected in Scala because is public in Java
  def handleEvent(state: S, event: Any): S

  /** @return The current state of the entity */
  def getState: S = _state

  /** @return All events emitted by command handlers of this entity up to now */
  def getAllEvents: JList[Any] = events

  protected def interpretEffects[R](effect: () => EventSourcedEntity.Effect[R]): EventSourcedResult[R] = {
    val commandContext = new TestKitEventSourcedEntityCommandContext()
    val effectExecuted =
      try {
        entity._internalSetCommandContext(Optional.of(commandContext))
        val effectExecuted = effect()
        this.events.addAll(EventSourcedResultImpl.eventsOf(effectExecuted))
        effectExecuted
      } finally {
        entity._internalSetCommandContext(Optional.empty)
      }
    try {
      entity._internalSetEventContext(Optional.of(new TestKitEventSourcedEntityEventContext()))
      this._state = EventSourcedResultImpl.eventsOf(effectExecuted).asScala.foldLeft(this._state)(handleEvent)
    } finally {
      entity._internalSetEventContext(Optional.empty)
    }
    val result =
      try {
        entity._internalSetCommandContext(Optional.of(commandContext))
        val secondaryEffect = EventSourcedResultImpl.secondaryEffectOf(effectExecuted, _state)
        new EventSourcedResultImpl[R, S](effectExecuted, _state, secondaryEffect)
      } finally {
        entity._internalSetCommandContext(Optional.empty)
      }
    result
  }
}
