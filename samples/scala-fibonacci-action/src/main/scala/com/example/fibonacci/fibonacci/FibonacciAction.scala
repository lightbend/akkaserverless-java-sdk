/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */
package com.example.fibonacci.fibonacci

import com.akkaserverless.scalasdk.action.Action
import com.akkaserverless.scalasdk.action.ActionCreationContext

/** An action. */
class FibonacciAction(creationContext: ActionCreationContext) extends AbstractFibonacciAction {

  private def isFibonacci(num: Long): Boolean = {
    val isPerfectSquare = (n: Long) => {
      val square = Math.sqrt(n.toDouble).toLong
      square * square == n
    }
    isPerfectSquare(5 * num * num + 4) || isPerfectSquare(5 * num * num - 4)
  }

  private def nextFib(num: Long): Long = {
    val result = num * (1 + Math.sqrt(5)) / 2.0;
    Math.round(result)
  }

  /** Handler for "NextNumber". */
  override def nextNumber(number: Number): Action.Effect[Number] = {
    val num = number.value
    if (isFibonacci(num))
      effects.reply(Number(nextFib(num)))
    else
      effects.error(s"Input number is not a Fibonacci number, received '$num'")
  }
}
