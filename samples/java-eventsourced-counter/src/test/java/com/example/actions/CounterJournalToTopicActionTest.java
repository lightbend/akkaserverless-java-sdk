/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */
package com.example.actions;

import com.akkaserverless.javasdk.testkit.ActionResult;
import com.akkaserverless.javasdk.testkit.ServiceCallDetails;
import com.akkaserverless.javasdk.testkit.impl.TestKitActionContext;
import com.example.actions.CounterJournalToTopicAction;
import com.example.actions.CounterJournalToTopicActionTestKit;
import com.example.actions.CounterTopicApi;
import com.example.domain.CounterDomain;
import com.example.CounterApi;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;


public class CounterJournalToTopicActionTest {

  @Test
  public void increaseTest() {
    CounterJournalToTopicActionTestKit testKit = CounterJournalToTopicActionTestKit.of(CounterJournalToTopicAction::new);
    ActionResult<CounterTopicApi.Increased> result = testKit.increase(CounterDomain.ValueIncreased.newBuilder().setValue(1).build());
    assertEquals(1, result.getReply().getValue());
    ServiceCallDetails sideEffect = result.getSideEffects().get(0);
    assertEquals("com.example.CounterService", sideEffect.getServiceName());
    assertEquals("GetCurrentCounter", sideEffect.getMethodName());
    //default eventSubject() from TestKitActionContext stubbing the ActionContext of the CounterJournalToTopicAction
    CounterApi.GetCounter counterId = CounterApi.GetCounter.newBuilder().setCounterId(new TestKitActionContext().eventSubject().get()).build(); 
    assertEquals(counterId, sideEffect.getMessage());
  }

  @Test
  public void decreaseTest() {
    CounterJournalToTopicActionTestKit testKit = CounterJournalToTopicActionTestKit.of(CounterJournalToTopicAction::new);
    ActionResult<CounterTopicApi.Decreased> result = testKit.decrease(CounterDomain.ValueDecreased.newBuilder().setValue(1).build());
    assertEquals(1, result.getReply().getValue());
  }
}
