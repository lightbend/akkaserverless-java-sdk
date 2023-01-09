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

package kalix.testkit.workflow

import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.{ Message => JavaPbMessage }
import io.grpc.Status
import kalix.protocol.component
import kalix.protocol.entity.Command
import kalix.protocol.workflow_entity.WorkflowStreamIn.{ Message => InMessage }
import kalix.protocol.workflow_entity.WorkflowStreamOut.{ Message => OutMessage }
import kalix.protocol.workflow_entity._
import kalix.testkit.entity.EntityMessages
import scalapb.{ GeneratedMessage => ScalaPbMessage }

object WorkflowMessages extends EntityMessages {

  def init(serviceName: String, workflowId: String): InMessage =
    init(serviceName, workflowId, Some(WorkflowSnapshot()))

  def init(serviceName: String, workflowId: String, payload: JavaPbMessage): InMessage = {
    // FIXME: workflow payload should not be a snapshot
    // entity in proxy is ES, but its snapshot is not the workflow state
    init(serviceName, workflowId, WorkflowSnapshot(1, messagePayload(payload)))
  }

  def init(serviceName: String, workflowId: String, payload: ScalaPbMessage): InMessage =
    init(serviceName, workflowId, WorkflowSnapshot(1, messagePayload(payload)))

  def init(serviceName: String, workflowId: String, state: WorkflowSnapshot): InMessage =
    init(serviceName, workflowId, Some(state))

  def init(serviceName: String, workflowId: String, state: Option[WorkflowSnapshot]): InMessage =
    InMessage.Init(WorkflowInit(serviceName, workflowId, state))

  def command(id: Long, workflowId: String, name: String): InMessage =
    command(id, workflowId, name, EmptyJavaMessage)

  def command(id: Long, workflowId: String, name: String, payload: JavaPbMessage): InMessage =
    command(id, workflowId, name, messagePayload(payload))

  def command(id: Long, workflowId: String, name: String, payload: ScalaPbMessage): InMessage =
    command(id, workflowId, name, messagePayload(payload))

  def command(id: Long, workflowId: String, name: String, payload: Option[ScalaPbAny]): InMessage =
    InMessage.Command(Command(workflowId, id, name, payload))

  def executeStep(id: Long, stepName: String, input: JavaPbMessage, userState: JavaPbMessage): InMessage = {
    val executeStep =
      ExecuteStep.defaultInstance
        .withCommandId(id)
        .withStepName(stepName)
        .withInput(protobufAny(input))
        .withUserState(protobufAny(userState))

    InMessage.Step(executeStep)
  }

  def getNextStep(id: Long, stepName: String, input: JavaPbMessage, userState: JavaPbMessage): InMessage = {
    val nextStep =
      GetNextStep.defaultInstance
        .withCommandId(id)
        .withStepName(stepName)
        .withResult(protobufAny(input))
        .withUserState(protobufAny(userState))
    InMessage.Transition(nextStep)
  }

  def actionFailure(id: Long, description: String, statusCode: Status.Code): OutMessage = {
    val failure = component.Failure(id, description, statusCode.value())
    OutMessage.Failure(failure)

  }
}
