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

package com.akkaserverless.javasdk.impl.valueentity

import java.util.Optional

import com.akkaserverless.javasdk.valueentity.CommandContext
import com.akkaserverless.javasdk.impl.EntityExceptions
import com.akkaserverless.javasdk.valueentity.ValueEntityBase

object ValueEntityHandler {
  final case class CommandResult(effect: ValueEntityBase.Effect[_])

  final case class CommandHandlerNotFound(commandName: String) extends RuntimeException

}

/**
 * @tparam S the type of the managed state for the entity
 * Not for manual user extension or interaction
 *
 * The concrete <code>ValueEntityHandler</code> is generated for the specific entities defined in Protobuf.
 */
abstract class ValueEntityHandler[S, E <: ValueEntityBase[S]](protected val entity: E) {
  import ValueEntityHandler._

  // "public" api against the impl/testkit
  final def handleCommand(commandName: String,
                          state: Option[Any],
                          command: Any,
                          context: CommandContext): CommandResult = {
    val commandEffect = try {
      entity.setCommandContext(Optional.of(context))
      handleCommand(commandName, state.asInstanceOf[Option[S]].getOrElse(entity.emptyState()), command, context)
        .asInstanceOf[ValueEntityEffectImpl[Any]]
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

    CommandResult(commandEffect)
  }

  protected def handleCommand(commandName: String,
                              state: S,
                              command: Any,
                              context: CommandContext): ValueEntityBase.Effect[_]

}
