/* This code was generated by Kalix tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */
package com.example.fibonacci

import akka.NotUsed
import akka.stream.scaladsl.Source
import kalix.scalasdk.action.Action
import kalix.scalasdk.action.ActionCreationContext

class FibonacciAction(creationContext: ActionCreationContext) extends AbstractFibonacciAction { // <1>

  // tag::implemented-action[]
  private def isFibonacci(num: Long): Boolean = { // <1>
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

  override def nextNumber(number: Number): Action.Effect[Number] = {
    val num = number.value
    if (isFibonacci(num)) // <2>
      effects.reply(Number(nextFib(num)))
    else
      effects.error(s"Input number is not a Fibonacci number, received '$num'") // <3>
  }
  // end::implemented-action[]

  override def nextNumbers(number: Number): Source[Action.Effect[Number], NotUsed] =
    if (!isFibonacci(number.value)) Source.failed(new IllegalArgumentException(s"Input number is not a Fibonacci number, received '${number.value}'"))
    else
      Source.unfold(number.value) { previous =>
        val next = nextFib(previous)
        Some((next, effects.reply(Number(next))))
      }

  override def nextNumberOfSum(numberSrc: Source[Number, NotUsed]): Action.Effect[Number] = {
    // contrived but just to stay in fibonacci land with a streamed in call
    implicit val materializer = actionContext.materializer()
    val futureEffect = numberSrc.runFold(0L)((acc, number) => acc + number.value)
      .map { sum =>
        if (!isFibonacci(sum)) effects.error(s"Input sum is not a Fibonacci number, received '$sum'")
        else effects.reply(Number(nextFib(sum)))
      }
    effects.asyncEffect(futureEffect)
  }

  override def nextNumberOfEach(numberSrc: Source[Number, NotUsed]): Source[Action.Effect[Number], NotUsed] = {
    numberSrc.map(number =>
      if (!isFibonacci(number.value)) effects.error(s"Input number is not a Fibonacci number, received '${number.value}'")
      else effects.reply(Number(nextFib(number.value)))
    )
  }

}
