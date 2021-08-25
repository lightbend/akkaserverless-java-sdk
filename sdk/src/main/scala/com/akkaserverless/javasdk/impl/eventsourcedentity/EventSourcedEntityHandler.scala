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

package com.akkaserverless.javasdk.impl.eventsourcedentity

import java.util.Optional

import com.akkaserverless.javasdk.eventsourcedentity.CommandContext
import com.akkaserverless.javasdk.eventsourcedentity.EventContext
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityBase
import com.akkaserverless.javasdk.impl.EntityExceptions
import com.akkaserverless.javasdk.impl.effect.SecondaryEffectImpl
import com.akkaserverless.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.EmitEvents
import com.akkaserverless.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.NoPrimaryEffect

object EventSourcedEntityHandler {
  final case class CommandResult(events: Vector[Any],
                                 secondaryEffect: SecondaryEffectImpl,
                                 snapshot: Option[Any],
                                 endSequenceNumber: Long)

  final case class CommandHandlerNotFound(commandName: String) extends RuntimeException

  final case class EventHandlerNotFound(eventClass: Class[_]) extends RuntimeException
}

/**
 * @tparam S the type of the managed state for the entity
 * Not for manual user extension or interaction
 *
 * The concrete <code>EventSourcedEntityHandler</code> is generated for the specific entities defined in Protobuf.
 */
abstract class EventSourcedEntityHandler[S, E <: EventSourcedEntityBase[S]](protected val entity: E) {
  import EventSourcedEntityHandler._

  private var state: Option[S] = None

  final protected def stateOrEmpty(): S = state match {
    case None =>
      val emptyState = entity.emptyState()
      // FIXME null should be allowed, issue #167
      require(emptyState != null, "Entity empty state is not allowed to be null")
      state = Option(emptyState)
      emptyState
    case Some(state) => state
  }

  private def setState(newState: S): Unit =
    state = Option(newState)

  // "public" api against the impl/testkit
  final def handleSnapshot(snapshot: S): Unit = setState(snapshot)
  final def handleEvent(event: Object, context: EventContext): Unit = {
    entity.setEventContext(Optional.of(context))
    try {
      val newState = handleEvent(stateOrEmpty(), event)
      setState(newState)
    } catch {
      case EventHandlerNotFound(eventClass) =>
        throw new IllegalArgumentException(s"Unknown event type [$eventClass] on ${entity.getClass}")
    } finally {
      entity.setEventContext(Optional.empty())
    }
  }
  final def handleCommand(commandName: String,
                          command: Any,
                          context: CommandContext,
                          snapshotEvery: Int,
                          eventContextFactory: Long => EventContext): CommandResult = {
    val commandEffect = try {
      entity.setCommandContext(Optional.of(context))
      handleCommand(commandName, stateOrEmpty(), command, context).asInstanceOf[EventSourcedEntityEffectImpl[Any]]
    } catch {
      case CommandHandlerNotFound(name) =>
        throw new EntityExceptions.EntityException(
          context.entityId(),
          context.commandId(),
          commandName,
          s"No command handler found for command [$name] on ${entity.getClass}"
        )
    } finally {
      entity.setCommandContext(Optional.empty())
    }
    var currentSequence = context.sequenceNumber()
    commandEffect.primaryEffect match {
      case EmitEvents(events) =>
        var shouldSnapshot = false
        events.foreach { event =>
          try {
            entity.setEventContext(Optional.of(eventContextFactory(currentSequence)))
            val newState = handleEvent(stateOrEmpty(), event)
            setState(newState)
          } catch {
            case EventHandlerNotFound(eventClass) =>
              throw new IllegalArgumentException(s"Unknown event type [$eventClass] on ${entity.getClass}")
          } finally {
            entity.setEventContext(Optional.empty())
          }
          currentSequence += 1
          shouldSnapshot = shouldSnapshot || (snapshotEvery > 0 && currentSequence % snapshotEvery == 0)
        }
        // FIXME currently snapshotting final state after applying all events even if trigger was mid-event stream?
        val endState = stateOrEmpty()
        val snapshot =
          if (shouldSnapshot) Option(endState)
          else None
        CommandResult(events.toVector, commandEffect.secondaryEffect(endState), snapshot, currentSequence)
      case NoPrimaryEffect =>
        CommandResult(Vector.empty, commandEffect.secondaryEffect(stateOrEmpty()), None, context.sequenceNumber())
    }
  }

  protected def handleEvent(state: S, event: Any): S

  protected def handleCommand(commandName: String,
                              state: S,
                              command: Any,
                              context: CommandContext): EventSourcedEntityBase.Effect[_]

}
