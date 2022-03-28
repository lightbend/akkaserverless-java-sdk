package org.example.valueentity

import kalix.scalasdk.valueentity.ValueEntity
import com.google.protobuf.empty.Empty
import org.example.Components
import org.example.ComponentsImpl
import org.example.valueentity

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

abstract class AbstractCounter extends ValueEntity[CounterState] {

  def components: Components =
    new ComponentsImpl(commandContext())

  def increase(currentState: CounterState, increaseValue: IncreaseValue): ValueEntity.Effect[Empty]

  def decrease(currentState: CounterState, decreaseValue: DecreaseValue): ValueEntity.Effect[Empty]

}

