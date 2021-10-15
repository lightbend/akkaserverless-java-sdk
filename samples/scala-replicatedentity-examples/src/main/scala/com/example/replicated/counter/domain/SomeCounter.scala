package com.example.replicated.counter.domain

import com.akkaserverless.scalasdk.replicatedentity.ReplicatedCounter
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntity
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntityContext
import com.example.replicated.counter
import com.google.protobuf.empty.Empty

// This class was initially generated based on the .proto definition by Akka Serverless tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

/** A replicated entity. */
class SomeCounter(context: ReplicatedEntityContext) extends AbstractSomeCounter {

  // tag::update[]
  def increase(currentData: ReplicatedCounter, increaseValue: counter.IncreaseValue): ReplicatedEntity.Effect[Empty] =
    effects
      .update(currentData.increment(increaseValue.value)) // <1>
      .thenReply(Empty.defaultInstance)

  def decrease(currentData: ReplicatedCounter, decreaseValue: counter.DecreaseValue): ReplicatedEntity.Effect[Empty] =
    effects
      .update(currentData.decrement(decreaseValue.value)) // <1>
      .thenReply(Empty.defaultInstance)
  // end::update[]

  // tag::get[]
  def get(currentData: ReplicatedCounter, getValue: counter.GetValue): ReplicatedEntity.Effect[counter.CurrentValue] =
    effects
      .reply(counter.CurrentValue(currentData.value)) // <1>
  // end::get[]
}
