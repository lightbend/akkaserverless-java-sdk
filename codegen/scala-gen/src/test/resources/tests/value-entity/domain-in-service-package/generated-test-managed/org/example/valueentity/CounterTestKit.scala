package org.example.valueentity

import com.akkaserverless.scalasdk.testkit.ValueEntityResult
import com.akkaserverless.scalasdk.testkit.impl.TestKitValueEntityContext
import com.akkaserverless.scalasdk.testkit.impl.ValueEntityResultImpl
import com.akkaserverless.scalasdk.valueentity.ValueEntity
import com.akkaserverless.scalasdk.valueentity.ValueEntityContext
import com.google.protobuf.empty.Empty
import org.example.valueentity

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * TestKit for unit testing Counter
 */
object CounterTestKit {
  /**
   * Create a testkit instance of Counter
   * @param entityFactory A function that creates a Counter based on the given ValueEntityContext,
   *                      a default entity id is used.
   */
  def apply(entityFactory: ValueEntityContext => Counter): CounterTestKit =
    apply("testkit-entity-id", entityFactory)

  /**
   * Create a testkit instance of Counter with a specific entity id.
   */
  def apply(entityId: String, entityFactory: ValueEntityContext => Counter): CounterTestKit =
    new CounterTestKit(entityFactory(new TestKitValueEntityContext(entityId)))
}

/**
 * TestKit for unit testing Counter
 */
final class CounterTestKit private(entity: Counter) {
  private var state: CounterState = entity.emptyState

  /**
   * @return The current state of the Counter under test
   */
  def currentState(): CounterState =
    state

  private def interpretEffects[Reply](effect: ValueEntity.Effect[Reply]): ValueEntityResult[Reply] = {
    val result = new ValueEntityResultImpl[Reply](effect)
    if (result.stateWasUpdated)
      this.state = result.updatedState.asInstanceOf[CounterState]
    result
  }

  def increase(command: IncreaseValue): ValueEntityResult[Empty] = {
    val effect = entity.increase(state, command)
    interpretEffects(effect)
  }

  def decrease(command: DecreaseValue): ValueEntityResult[Empty] = {
    val effect = entity.decrease(state, command)
    interpretEffects(effect)
  }
}
