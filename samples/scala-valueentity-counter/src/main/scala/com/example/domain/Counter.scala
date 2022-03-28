/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package com.example.domain

import kalix.scalasdk.valueentity.ValueEntity
import kalix.scalasdk.valueentity.ValueEntityContext
import com.example
import com.example.CurrentCounter
import com.google.protobuf.empty.Empty

// tag::class[]
class Counter(context: ValueEntityContext) extends AbstractCounter { // <1>

  override def emptyState: CounterState = CounterState() // <2>
  // end::class[]

  // tag::increase[]
  override def increase(currentState: CounterState, command: example.IncreaseValue): ValueEntity.Effect[Empty] =
    if (command.value < 0) // <1>
      effects.error(s"Increase requires a positive value. It was [${command.value}].")
    else {
      val newState = currentState.copy(value = currentState.value + command.value) // <2>
      effects
        .updateState(newState) // <3>
        .thenReply(Empty.defaultInstance) // <4>
    }
  // end::increase[]

  override def decrease(currentState: CounterState, command: example.DecreaseValue): ValueEntity.Effect[Empty] =
    if (command.value < 0) effects.error(s"Increase requires a positive value. It was [${command.value}].")
    else
      effects
        .updateState(currentState.copy(value = currentState.value - command.value))
        .thenReply(Empty.defaultInstance)

  override def reset(currentState: CounterState, command: example.ResetValue): ValueEntity.Effect[Empty] =
    effects.updateState(CounterState()).thenReply(Empty.defaultInstance)

  // tag::getCurrentCounter[]
  override def getCurrentCounter(
      currentState: CounterState, // <1>
      command: example.GetCounter): ValueEntity.Effect[example.CurrentCounter] =
    effects.reply(CurrentCounter(currentState.value)) // <2>
  // end::getCurrentCounter[]
}
