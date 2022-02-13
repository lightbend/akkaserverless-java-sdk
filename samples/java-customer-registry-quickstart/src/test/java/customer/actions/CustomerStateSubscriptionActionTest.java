package customer.actions;

import akka.stream.javadsl.Source;
import com.akkaserverless.javasdk.testkit.ActionResult;
import com.google.protobuf.Empty;
import customer.actions.CustomerStateSubscriptionAction;
import customer.actions.CustomerStateSubscriptionActionTestKit;
import customer.domain.CustomerDomain;
import org.junit.Test;
import static org.junit.Assert.*;

// This class was initially generated based on the .proto definition by Akka Serverless tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

public class CustomerStateSubscriptionActionTest {

  @Test
  public void exampleTest() {
    CustomerStateSubscriptionActionTestKit testKit = CustomerStateSubscriptionActionTestKit.of(CustomerStateSubscriptionAction::new);
    // use the testkit to execute a command
    // ActionResult<SomeResponse> result = testKit.someOperation(SomeRequest);
    // verify the response
    // SomeResponse actualResponse = result.getReply();
    // assertEquals(expectedResponse, actualResponse);
  }

  @Test
  public void onUpdateStateTest() {
    CustomerStateSubscriptionActionTestKit testKit = CustomerStateSubscriptionActionTestKit.of(CustomerStateSubscriptionAction::new);
    // ActionResult<Empty> result = testKit.onUpdateState(CustomerDomain.CustomerState.newBuilder()...build());
  }

}
