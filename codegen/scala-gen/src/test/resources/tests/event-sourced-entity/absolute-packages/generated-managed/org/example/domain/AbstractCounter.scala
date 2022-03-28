package org.example.domain

import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import com.google.protobuf.empty.Empty
import org.example.Components
import org.example.ComponentsImpl
import org.example.events.Decreased
import org.example.events.Increased
import org.example.eventsourcedentity
import org.example.state.CounterState

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

abstract class AbstractCounter extends EventSourcedEntity[CounterState] {

  def components: Components =
    new ComponentsImpl(commandContext())

  def increase(currentState: CounterState, increaseValue: eventsourcedentity.IncreaseValue): EventSourcedEntity.Effect[Empty]

  def decrease(currentState: CounterState, decreaseValue: eventsourcedentity.DecreaseValue): EventSourcedEntity.Effect[Empty]

  def increased(currentState: CounterState, increased: Increased): CounterState
  def decreased(currentState: CounterState, decreased: Decreased): CounterState
}

