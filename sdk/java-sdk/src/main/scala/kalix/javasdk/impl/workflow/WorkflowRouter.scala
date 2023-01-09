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

package kalix.javasdk.impl.workflow

import java.util.Optional
import java.util.function.{ Function => JFunc }

import scala.jdk.OptionConverters.RichOptional

import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.javasdk.DeferredCall
import kalix.javasdk.impl.GrpcDeferredCall
import kalix.javasdk.impl.MessageCodec
import kalix.javasdk.impl.MetadataImpl
import kalix.javasdk.impl.RestDeferredCall
import kalix.javasdk.impl.workflow.WorkflowExceptions.ProtocolException
import kalix.javasdk.impl.workflow.WorkflowRouter.CommandHandlerNotFound
import kalix.javasdk.impl.workflow.WorkflowRouter.CommandResult
import kalix.javasdk.impl.workflow.WorkflowRouter.WorkflowStepNotFound
import kalix.javasdk.workflow.CommandContext
import kalix.javasdk.workflow.Workflow
import kalix.javasdk.workflow.Workflow.Call
import kalix.javasdk.workflow.Workflow.Effect
import kalix.protocol.workflow_entity.StepDeferredCall
import kalix.protocol.workflow_entity.StepResponse

object WorkflowRouter {
  final case class CommandResult(effect: Workflow.Effect[_])

  final case class CommandHandlerNotFound(commandName: String) extends RuntimeException
  final case class WorkflowStepNotFound(stepName: String) extends RuntimeException
}

abstract class WorkflowRouter[S, W <: Workflow[S]](protected val workflow: W) {

  private var state: Option[S] = None

  private def stateOrEmpty(): S = state match {
    case None =>
      val emptyState = workflow.emptyState()
      // null is allowed as emptyState
      state = Some(emptyState)
      emptyState
    case Some(state) =>
      state
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  def _internalSetInitState(s: Any): Unit = {
    state = Some(s.asInstanceOf[S])
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalHandleCommand(commandName: String, command: Any, context: CommandContext): CommandResult = {
    val commandEffect =
      try {
        workflow._internalSetCommandContext(Optional.of(context))
        workflow._internalSetCurrentState(stateOrEmpty())
        handleCommand(commandName, stateOrEmpty(), command, context).asInstanceOf[Effect[Any]]
      } catch {
        case CommandHandlerNotFound(name) =>
          throw new WorkflowExceptions.WorkflowException(
            context.workflowId(),
            context.commandId(),
            commandName,
            s"No command handler found for command [$name] on ${workflow.getClass}")
      } finally {
        workflow._internalSetCommandContext(Optional.empty())
      }

    CommandResult(commandEffect)
  }

  protected def handleCommand(commandName: String, state: S, command: Any, context: CommandContext): Workflow.Effect[_]

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalHandleStep(input: ScalaPbAny, stepName: String, messageCodec: MessageCodec): StepResponse = {

    workflow._internalSetCurrentState(stateOrEmpty())
    val workflowDef = workflow.definition()

    workflowDef.findByName(stepName).toScala match {
      case Some(call: Call[_, _, _]) =>
        val defCall =
          call.callFunc
            .asInstanceOf[JFunc[Any, DeferredCall[Any, Any]]]
            .apply(messageCodec.decodeMessage(input))

        val (commandName, serviceName) =
          defCall match {
            case grpcDefCall: GrpcDeferredCall[_, _] =>
              (grpcDefCall.methodName, grpcDefCall.fullServiceName)
            case restDefCall: RestDeferredCall[_, _] =>
              (restDefCall.methodName, restDefCall.fullServiceName)
          }

        val stepDefCall =
          StepDeferredCall(
            serviceName,
            commandName,
            payload = Some(messageCodec.encodeScala(defCall.message())),
            metadata = MetadataImpl.toProtocol(defCall.metadata()))

        StepResponse.defaultInstance
          .withResponse(StepResponse.Response.DeferredCall(stepDefCall))

      case Some(_) => throw ProtocolException("Unknown step type") // just making compiler happy
      case None    => throw WorkflowStepNotFound(stepName)
    }

  }

  def _internalGetNextStep(stepName: String, result: ScalaPbAny, messageCodec: MessageCodec): CommandResult = {

    workflow._internalSetCurrentState(stateOrEmpty())
    val workflowDef = workflow.definition()

    workflowDef.findByName(stepName).toScala match {
      case Some(call: Call[_, _, _]) =>
        val effect =
          call.transitionFunc
            .asInstanceOf[JFunc[Any, Effect[Any]]]
            .apply(messageCodec.decodeMessage(result))

        CommandResult(effect)

      case Some(_) => throw ProtocolException("Unknown step type") // just making compiler happy
      case None    => throw WorkflowStepNotFound(stepName)
    }
  }
}
