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

package kalix.scalasdk.impl.timer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.ScalaDurationOps

import akka.Done
import akka.actor.ActorSystem
import com.google.protobuf.duration.{ Duration => ProtoDuration }
import com.google.protobuf.wrappers.StringValue
import kalix.javasdk.impl.AnySupport
import kalix.javasdk.impl.DeferredCallImpl
import kalix.javasdk.impl.GrpcClients
import kalix.scalasdk.DeferredCall
import kalix.scalasdk.impl.ScalaDeferredCallAdapter
import kalix.scalasdk.timer.TimerScheduler
import kalix.timers.timers.Call
import kalix.timers.timers.SingleTimer
import kalix.timers.timers.TimerService

/** INTERNAL API */
private[kalix] final class TimerSchedulerImpl(anySupport: AnySupport, system: ActorSystem) extends TimerScheduler {

  /**
   * TODO - document
   */
  override def startSingleTimer[I, O](
      name: String,
      delay: FiniteDuration,
      deferredCall: DeferredCall[I, O]): Future[Done] = {

    val timerServiceClient = GrpcClients(system).getProxyGrpcClient(classOf[TimerService])

    val deferredCallImpl =
      deferredCall match {
        case ScalaDeferredCallAdapter(deferredCallImpl: DeferredCallImpl[I, O] @unchecked) => deferredCallImpl
        // should not happen as we always need to pass ScalaDeferredCallAdapter(DeferredCallImpl)
        case other =>
          throw new RuntimeException(
            s"Incompatible DeferredCall instance. Found ${other.getClass}, expecting ${classOf[DeferredCallImpl[_, _]].getName}")
      }

    val call =
      Call(
        deferredCallImpl.fullServiceName,
        deferredCallImpl.methodName,
        Some(anySupport.encodeScala(deferredCall.message)))

    val singleTimer = SingleTimer(name, Some(call), Some(ProtoDuration(delay.toJava)))
    timerServiceClient.addSingle(singleTimer).map(_ => Done)(ExecutionContext.parasitic)
  }

  /**
   * TODO - document
   */
  override def cancel(name: String): Future[Done] = {
    val timerServiceClient = GrpcClients(system).getProxyGrpcClient(classOf[TimerService])
    timerServiceClient.remove(StringValue(name)).map(_ => Done)(ExecutionContext.parasitic)
  }
}
