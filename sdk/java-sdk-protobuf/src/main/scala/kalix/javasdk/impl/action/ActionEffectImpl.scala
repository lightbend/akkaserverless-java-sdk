/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.action

import io.grpc.Status
import kalix.javasdk.StatusCode.ErrorCode
import kalix.javasdk._
import kalix.javasdk.action.Action
import kalix.javasdk.impl.StatusCodeConverter
import kalix.javasdk.impl.telemetry.Telemetry

import java.util
import java.util.concurrent.CompletionStage
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.CompletionStageOps

/** INTERNAL API */
object ActionEffectImpl {
  sealed abstract class PrimaryEffect[T] extends Action.Effect[T] {
    override def addSideEffect(sideEffects: SideEffect*): Action.Effect[T] =
      withSideEffects(internalSideEffects ++ sideEffects)
    override def addSideEffects(sideEffects: util.Collection[SideEffect]): Action.Effect[T] =
      withSideEffects(internalSideEffects ++ sideEffects.asScala)
    override def canHaveSideEffects: Boolean = true
    def internalSideEffects: Seq[SideEffect]
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

    override def internalSideEffects: Seq[SideEffect] = Nil

    protected def withSideEffects(sideEffect: Seq[SideEffect]): PrimaryEffect[Nothing] = {
      throw new IllegalArgumentException("adding side effects to 'ignore' is not allowed.")
    }
  }

  class Builder(val actionContextMetadata: Metadata) extends Action.Effect.Builder {

    def reply[S](message: S): Action.Effect[S] = {
      message match {
        case httpResponse: HttpResponse =>
          ReplyEffect(message, Some(Metadata.EMPTY.withStatusCode(httpResponse.getStatusCode).addTracing()), Nil)
        case _ => ReplyEffect(message, Some(Metadata.EMPTY.addTracing()), Nil)
      }
    }
    def reply[S](message: S, metadata: Metadata): Action.Effect[S] = {
      message match {
        case httpResponse: HttpResponse =>
          ReplyEffect(message, Some(metadata.withStatusCode(httpResponse.getStatusCode).addTracing()), Nil)
        case _ => ReplyEffect(message, Some(metadata.addTracing()), Nil)
      }
      ReplyEffect(message, Some(metadata.addTracing()), Nil)
    }
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
      AsyncEffect(futureMessage.asScala.map(s => reply[S](s, metadata.addTracing()))(ExecutionContext.parasitic), Nil)
    def asyncEffect[S](futureEffect: CompletionStage[Action.Effect[S]]): Action.Effect[S] =
      AsyncEffect(futureEffect.asScala, Nil)
    def ignore[S](): Action.Effect[S] =
      IgnoreEffect()

    import scala.jdk.OptionConverters._

    implicit class TracingWrapper(metadata: Metadata) {
      def addTracing(): Metadata = {
        actionContextMetadata.traceContext().traceParent().toScala match {
          case Some(traceparent) if !metadata.has(Telemetry.TRACE_PARENT_KEY) =>
            metadata.add(Telemetry.TRACE_PARENT_KEY, traceparent)
          case _ => metadata
        }
      }
    }

  }

  def builder(context: Metadata): Action.Effect.Builder = new Builder(context)

}
