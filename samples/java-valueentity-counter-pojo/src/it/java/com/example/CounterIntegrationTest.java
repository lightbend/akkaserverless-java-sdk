/* This code was initialised by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package com.example;

import com.example.CounterApi;
import com.example.CounterService;
import com.akkaserverless.javasdk.testkit.junit.AkkaServerlessTestKitResource;
import com.example.Main;
import org.junit.ClassRule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;

import static java.util.concurrent.TimeUnit.*;
import static org.hamcrest.MatcherAssert.assertThat;


// Run all test classes ending with "IntegrationTest" using `mvn verify -Pit`
// tag::sample-it-test[]
public class CounterIntegrationTest {
    
  /**
   * The test kit starts both the service container and the Akka Serverless proxy.
   */
  @ClassRule
  public static final AkkaServerlessTestKitResource testKit =
          new AkkaServerlessTestKitResource(Main.createAkkaServerless());
  
  /**
   * Use the generated gRPC client to call the service through the Akka Serverless proxy.
   */
  private final CounterService client;
  
  public CounterIntegrationTest() {
      client = testKit.getGrpcClient(CounterService.class);
  }
  
  @Test
  public void increaseOnNonExistingEntity() throws Exception {
      String entityId = "new-id";
      client.increase(CounterApi.IncreaseValue.newBuilder().setCounterId(entityId).setValue(42).build())
               .toCompletableFuture().get(5, SECONDS);
      CounterApi.CurrentCounter reply = client.getCurrentCounter(CounterApi.GetCounter.newBuilder().setCounterId(entityId).build())
              .toCompletableFuture().get(5, SECONDS);
      assertThat(reply.getValue(), is(42));
  }
  // end::sample-it-test[]
  
  @Test
  public void increase() throws Exception {
      String entityId = "another-id";
      client.increase(CounterApi.IncreaseValue.newBuilder().setCounterId(entityId).setValue(42).build())
               .toCompletableFuture().get(5, SECONDS);
      client.increase(CounterApi.IncreaseValue.newBuilder().setCounterId(entityId).setValue(27).build())
               .toCompletableFuture().get(5, SECONDS);
      CounterApi.CurrentCounter reply = client.getCurrentCounter(CounterApi.GetCounter.newBuilder().setCounterId(entityId).build())
              .toCompletableFuture().get(5, SECONDS);
      assertThat(reply.getValue(), is(69));
  }
}
