package org.example.eventsourcedentity

import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityContext
import kalix.scalasdk.testkit.EventSourcedResult
import kalix.scalasdk.testkit.impl.EventSourcedEntityEffectsRunner
import kalix.scalasdk.testkit.impl.EventSourcedResultImpl
import kalix.scalasdk.testkit.impl.TestKitEventSourcedEntityCommandContext
import kalix.scalasdk.testkit.impl.TestKitEventSourcedEntityContext
import kalix.scalasdk.testkit.impl.TestKitEventSourcedEntityEventContext
import com.google.protobuf.empty.Empty
import org.example.eventsourcedentity

import scala.collection.immutable.Seq

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * TestKit for unit testing Counter
 */
object CounterTestKit {
  /**
   * Create a testkit instance of Counter
   * @param entityFactory A function that creates a Counter based on the given EventSourcedEntityContext,
   *                      a default entity id is used.
   */
  def apply(entityFactory: EventSourcedEntityContext => Counter): CounterTestKit =
    apply("testkit-entity-id", entityFactory)
  /**
   * Create a testkit instance of Counter with a specific entity id.
   */
  def apply(entityId: String, entityFactory: EventSourcedEntityContext => Counter): CounterTestKit =
    new CounterTestKit(entityFactory(new TestKitEventSourcedEntityContext(entityId)))
}
final class CounterTestKit private(entity: Counter) extends EventSourcedEntityEffectsRunner[CounterState](entity: Counter) {

  override protected def handleEvent(state: CounterState, event: Any): CounterState = {
    event match {
      case e: Increased =>
        entity.increased(state, e)

      case e: Decreased =>
        entity.decreased(state, e)
    }
  }

  def increase(command: IncreaseValue): EventSourcedResult[Empty] =
    interpretEffects(() => entity.increase(currentState, command))

  def decrease(command: DecreaseValue): EventSourcedResult[Empty] =
    interpretEffects(() => entity.decrease(currentState, command))
}
