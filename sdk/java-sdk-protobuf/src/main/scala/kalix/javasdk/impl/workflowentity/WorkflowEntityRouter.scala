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

package kalix.javasdk.impl.workflowentity

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunc }
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Future
import scala.jdk.OptionConverters.RichOptional
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.javasdk.DeferredCall
import kalix.javasdk.impl.EntityExceptions.EntityException
import kalix.javasdk.impl.GrpcDeferredCall
import kalix.javasdk.impl.MessageCodec
import kalix.javasdk.impl.MetadataImpl
import kalix.javasdk.impl.RestDeferredCall
import kalix.javasdk.impl.workflowentity.WorkflowEntityRouter.CommandHandlerNotFound
import kalix.javasdk.impl.workflowentity.WorkflowEntityRouter.CommandResult
import kalix.javasdk.impl.workflowentity.WorkflowEntityRouter.WorkflowStepNotFound
import kalix.javasdk.impl.workflowentity.WorkflowEntityRouter.WorkflowStepNotSupported
import kalix.javasdk.workflowentity.CommandContext
import kalix.javasdk.workflowentity.WorkflowEntity.Effect
import kalix.javasdk.workflowentity.WorkflowEntity
import kalix.javasdk.workflowentity.WorkflowEntity.AsyncCallStep
import kalix.javasdk.workflowentity.WorkflowEntity.CallStep
import kalix.javasdk.workflowentity.WorkflowEntityContext
import kalix.protocol.workflow_entity.StepDeferredCall
import kalix.protocol.workflow_entity.StepExecuted
import kalix.protocol.workflow_entity.StepResponse

object WorkflowEntityRouter {
  final case class CommandResult(effect: WorkflowEntity.Effect[_])

  final case class CommandHandlerNotFound(commandName: String) extends RuntimeException {
    override def getMessage: String = commandName
  }
  final case class WorkflowStepNotFound(stepName: String) extends RuntimeException {
    override def getMessage: String = stepName
  }

  final case class WorkflowStepNotSupported(stepName: String) extends RuntimeException {
    override def getMessage: String = stepName
  }
}

abstract class WorkflowEntityRouter[S, W <: WorkflowEntity[S]](protected val workflow: W) {

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

  def _getWorkflowDefinition(): WorkflowEntity.Workflow[S] = {
    workflow.definition()
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  def _internalSetInitState(s: Any): Unit = {
    state = Some(s.asInstanceOf[S])
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalHandleCommand(
      commandName: String,
      command: Any,
      context: CommandContext,
      workflowContext: WorkflowEntityContext): CommandResult = {
    val commandEffect =
      try {
        workflow._internalSetWorkflowContext(Optional.of(workflowContext))
        workflow._internalSetCommandContext(Optional.of(context))
        workflow._internalSetCurrentState(stateOrEmpty())
        handleCommand(commandName, stateOrEmpty(), command, context).asInstanceOf[Effect[Any]]
      } catch {
        case CommandHandlerNotFound(name) =>
          throw new EntityException(
            context.entityId(),
            context.commandId(),
            commandName,
            s"No command handler found for command [$name] on ${workflow.getClass}")
      } finally {
        workflow._internalSetCommandContext(Optional.empty())
      }

    CommandResult(commandEffect)
  }

  protected def handleCommand(
      commandName: String,
      state: S,
      command: Any,
      context: CommandContext): WorkflowEntity.Effect[_]

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalHandleStep(
      commandId: Long,
      input: Option[ScalaPbAny],
      stepName: String,
      messageCodec: MessageCodec,
      workflowContext: WorkflowEntityContext): Future[StepResponse] = {

    implicit val ec = workflowContext.materializer().executionContext

    workflow._internalSetCurrentState(stateOrEmpty())
    workflow._internalSetWorkflowContext(Optional.of(workflowContext))
    val workflowDef = workflow.definition()

    workflowDef.findByName(stepName).toScala match {
      case Some(call: CallStep[_, _, _, _]) =>
        val decodedInput = input match {
          case Some(inputValue) => messageCodec.decodeMessage(inputValue)
          case None             => null // to meet a signature of supplier expressed as a function
        }

        val defCall = call.callFunc
          .asInstanceOf[JFunc[Any, DeferredCall[Any, Any]]]
          .apply(decodedInput)

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

        Future.successful {
          StepResponse(commandId, stepName, StepResponse.Response.DeferredCall(stepDefCall))
        }

      case Some(call: AsyncCallStep[_, _, _]) =>
        val decodedInput = input match {
          case Some(inputValue) => messageCodec.decodeMessage(inputValue)
          case None             => null // to meet a signature of supplier expressed as a function
        }

        val future = call.callFunc
          .asInstanceOf[JFunc[Any, CompletionStage[Any]]]
          .apply(decodedInput)
          .toScala

        future.map { res =>
          val encoded = messageCodec.encodeScala(res)
          val executedRes = StepExecuted(Some(encoded))

          StepResponse(commandId, stepName, StepResponse.Response.Executed(executedRes))
        }
      case Some(any) => Future.failed(WorkflowStepNotSupported(any.getClass.getSimpleName))
      case None      => Future.failed(WorkflowStepNotFound(stepName))
    }

  }

  def _internalGetNextStep(stepName: String, result: ScalaPbAny, messageCodec: MessageCodec): CommandResult = {

    workflow._internalSetCurrentState(stateOrEmpty())
    val workflowDef = workflow.definition()

    workflowDef.findByName(stepName).toScala match {
      case Some(call: CallStep[_, _, _, _]) =>
        val effect =
          call.transitionFunc
            .asInstanceOf[JFunc[Any, Effect[Any]]]
            .apply(messageCodec.decodeMessage(result))

        CommandResult(effect)

      case Some(call: AsyncCallStep[_, _, _]) =>
        val effect =
          call.transitionFunc
            .asInstanceOf[JFunc[Any, Effect[Any]]]
            .apply(messageCodec.decodeMessage(result))

        CommandResult(effect)

      case Some(any) => throw WorkflowStepNotSupported(any.getClass.getSimpleName)
      case None      => throw WorkflowStepNotFound(stepName)
    }
  }
}
