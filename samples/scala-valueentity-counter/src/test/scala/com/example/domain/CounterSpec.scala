/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */
package com.example.domain

import com.akkaserverless.scalasdk.Metadata
import com.example.{ DecreaseValue, IncreaseValue, ResetValue }
import com.google.protobuf.empty.Empty
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// tag::sample-unit-test[]
class CounterSpec extends AnyWordSpec with Matchers {

  "Counter" must {

    "handle command Increase" in {
      val testKit = CounterTestKit(new Counter(_))

      val result1 = testKit.increase(IncreaseValue(value = 1))
      result1.reply shouldBe Empty.defaultInstance

      // one more time
      val result2 = testKit.increase(IncreaseValue(value = 1))
      result2.reply shouldBe Empty.defaultInstance
      testKit.currentState().value shouldBe 2
    }
    // end::sample-unit-test[]

    "handle command Increase depending on Metadata" in {
      val testKit = CounterTestKit(new Counter(_))

      val result1 = testKit.increaseWithConditional(
        IncreaseValue(value = 1), 
        Metadata.empty.set("myKey","myValue"))
      result1.reply shouldBe Empty.defaultInstance
      testKit.currentState().value shouldBe 2
    }

    "handle command Decrease" in {
      val testKit = CounterTestKit(new Counter(_))

      val result1 = testKit.increase(IncreaseValue(value = 1))
      result1.reply shouldBe Empty.defaultInstance
      testKit.currentState().value shouldBe 1

      val result2 = testKit.decrease(DecreaseValue(value = 1))
      result2.reply shouldBe Empty.defaultInstance
      testKit.currentState().value shouldBe 0
    }

    "handle command Reset" in {
      val testKit = CounterTestKit(new Counter(_))

      val result1 = testKit.increase(IncreaseValue(value = 1))
      result1.reply shouldBe Empty.defaultInstance
      testKit.currentState().value shouldBe 1

      val resetResult = testKit.reset(ResetValue())
      resetResult.reply shouldBe Empty.defaultInstance
      testKit.currentState().value shouldBe 0
    }

  }
}
