/*
 * Copyright 2024 Lightbend Inc.
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

package kalix.javasdk.impl.action

import kalix.javasdk.{ DeferredCall, Metadata, SideEffect }
import kalix.javasdk.action.Action

import java.util
import java.util.concurrent.CompletionStage
import io.grpc.Status
import kalix.javasdk.StatusCode
import kalix.javasdk.StatusCode.ErrorCode
import kalix.javasdk.impl.StatusCodeConverter

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.CompletionStageOps

/** INTERNAL API */
object ActionEffectImpl {
  sealed abstract class PrimaryEffect[T] extends Action.Effect[T] {
    override def addSideEffect(sideEffects: SideEffect*): Action.Effect[T] =
      withSideEffects(internalSideEffects() ++ sideEffects)
    override def addSideEffects(sideEffects: util.Collection[SideEffect]): Action.Effect[T] =
      withSideEffects(internalSideEffects() ++ sideEffects.asScala)
    override def canHaveSideEffects: Boolean = true
    def internalSideEffects(): Seq[SideEffect]
    protected def withSideEffects(sideEffects: Seq[SideEffect]): Action.Effect[T]
  }

  final case class ReplyEffect[T](msg: T, metadata: Option[Metadata], internalSideEffects: Seq[SideEffect])
      extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
    protected def withSideEffects(sideEffects: Seq[SideEffect]): ReplyEffect[T] =
      copy(internalSideEffects = sideEffects)
  }

  final case class AsyncEffect[T](effect: Future[Action.Effect[T]], internalSideEffects: Seq[SideEffect])
      extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
    protected def withSideEffects(sideEffects: Seq[SideEffect]): AsyncEffect[T] =
      copy(internalSideEffects = sideEffects)
  }

  final case class ForwardEffect[T](serviceCall: DeferredCall[_, T], internalSideEffects: Seq[SideEffect])
      extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
    protected def withSideEffects(sideEffects: Seq[SideEffect]): ForwardEffect[T] =
      copy(internalSideEffects = sideEffects)
  }

  final case class ErrorEffect[T](
      description: String,
      statusCode: Option[Status.Code],
      internalSideEffects: Seq[SideEffect])
      extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
    protected def withSideEffects(sideEffects: Seq[SideEffect]): ErrorEffect[T] =
      copy(internalSideEffects = sideEffects)
  }

  def IgnoreEffect[T](): PrimaryEffect[T] = IgnoreEffect.asInstanceOf[PrimaryEffect[T]]
  case object IgnoreEffect extends PrimaryEffect[Nothing] {
    def isEmpty: Boolean = true
    override def canHaveSideEffects: Boolean = false

    override def internalSideEffects() = Nil

    protected def withSideEffects(sideEffect: Seq[SideEffect]): PrimaryEffect[Nothing] = {
      throw new IllegalArgumentException("adding side effects to 'ignore' is not allowed.")
    }
  }

  object Builder extends Action.Effect.Builder {
    def reply[S](message: S): Action.Effect[S] = ReplyEffect(message, None, Nil)
    def reply[S](message: S, metadata: Metadata): Action.Effect[S] = ReplyEffect(message, Some(metadata), Nil)
    def forward[S](serviceCall: DeferredCall[_, S]): Action.Effect[S] = ForwardEffect(serviceCall, Nil)
    def error[S](description: String): Action.Effect[S] = ErrorEffect(description, None, Nil)
    def error[S](description: String, grpcErrorCode: Status.Code): Action.Effect[S] = {
      if (grpcErrorCode.toStatus.isOk) throw new IllegalArgumentException("Cannot fail with a success status")
      ErrorEffect(description, Some(grpcErrorCode), Nil)
    }
    def error[S](description: String, httpErrorCode: ErrorCode): Action.Effect[S] =
      error(description, StatusCodeConverter.toGrpcCode(httpErrorCode))
    def asyncReply[S](futureMessage: CompletionStage[S]): Action.Effect[S] =
      asyncReply(futureMessage, Metadata.EMPTY)
    def asyncReply[S](futureMessage: CompletionStage[S], metadata: Metadata): Action.Effect[S] =
      AsyncEffect(futureMessage.asScala.map(s => Builder.reply[S](s, metadata))(ExecutionContext.parasitic), Nil)
    def asyncEffect[S](futureEffect: CompletionStage[Action.Effect[S]]): Action.Effect[S] =
      AsyncEffect(futureEffect.asScala, Nil)
    def ignore[S](): Action.Effect[S] =
      IgnoreEffect()
  }

  def builder(): Action.Effect.Builder = Builder

}
