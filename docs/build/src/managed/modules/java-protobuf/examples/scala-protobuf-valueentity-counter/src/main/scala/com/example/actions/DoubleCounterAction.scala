/* This code was generated by Kalix tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */
package com.example.actions

import kalix.scalasdk.SideEffect
import kalix.scalasdk.action.Action
import kalix.scalasdk.action.ActionCreationContext
import com.example.IncreaseValue
import com.google.protobuf.empty.Empty

// tag::controller-forward[]
// tag::controller-side-effect[]
class DoubleCounterAction(creationContext: ActionCreationContext) extends AbstractDoubleCounterAction {


  override def increase(increaseValue: IncreaseValue): Action.Effect[Empty] = {
    // end::controller-side-effect[]
    val doubled = increaseValue.value * 2
    val increaseValueDoubled = increaseValue.copy(value = doubled) // <1>

    effects.forward(components.counter.increase(increaseValueDoubled)) // <2>
  }

  // end::controller-forward[]
  // tag::controller-side-effect[]
  override def increaseWithSideEffect(increaseValue: IncreaseValue): Action.Effect[Empty] = {
    val doubled = increaseValue.value * 2
    val increaseValueDoubled = increaseValue.copy(value = doubled) // <1>

    effects
      .reply(Empty.defaultInstance) // <2>
      .addSideEffect( // <3>
        SideEffect(components.counter.increase(increaseValueDoubled)))
  }
  // tag::controller-forward[]

}
// end::controller-forward[]
// end::controller-side-effect[]
