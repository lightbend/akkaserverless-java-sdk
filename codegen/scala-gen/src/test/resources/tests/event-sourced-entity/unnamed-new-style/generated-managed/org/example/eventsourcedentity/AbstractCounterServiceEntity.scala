package org.example.eventsourcedentity

import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import com.google.protobuf.empty.Empty
import org.example.Components
import org.example.ComponentsImpl
import org.example.eventsourcedentity
import org.example.eventsourcedentity.domain.CounterState
import org.example.eventsourcedentity.domain.Decreased
import org.example.eventsourcedentity.domain.Increased

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

abstract class AbstractCounterServiceEntity extends EventSourcedEntity[CounterState] {

  def components: Components =
    new ComponentsImpl(commandContext())

  def increase(currentState: CounterState, increaseValue: IncreaseValue): EventSourcedEntity.Effect[Empty]

  def decrease(currentState: CounterState, decreaseValue: DecreaseValue): EventSourcedEntity.Effect[Empty]

  def increased(currentState: CounterState, increased: Increased): CounterState
  def decreased(currentState: CounterState, decreased: Decreased): CounterState
}

