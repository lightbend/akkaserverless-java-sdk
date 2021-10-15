package com.example.replicated.set.domain

import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntity
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntityContext
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedSet
import com.example.replicated.set
import com.google.protobuf.empty.Empty

// This class was initially generated based on the .proto definition by Akka Serverless tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

/** A replicated entity. */
class SomeSet(context: ReplicatedEntityContext) extends AbstractSomeSet {

  // tag::update[]
  def add(currentData: ReplicatedSet[String], addElement: set.AddElement): ReplicatedEntity.Effect[Empty] = 
    effects
      .update(currentData.add(addElement.element)) // <1>
      .thenReply(Empty.defaultInstance)

  def remove(currentData: ReplicatedSet[String], removeElement: set.RemoveElement): ReplicatedEntity.Effect[Empty] =
    effects
      .update(currentData.remove(removeElement.element)) // <1>
      .thenReply(Empty.defaultInstance)
  // end::update[]

  // tag::get[]
  def get(currentData: ReplicatedSet[String], getElements: set.GetElements): ReplicatedEntity.Effect[set.CurrentElements] =
    effects.reply(set.CurrentElements(currentData.elements.toSeq)) // <1>
  // end::get[]
}
