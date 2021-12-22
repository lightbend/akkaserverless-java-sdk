package org.example;

import com.akkaserverless.javasdk.DeferredCall;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * Not intended for user extension, provided through generated implementation
 */
public interface Components {
  CounterCalls counter();

  interface CounterCalls {
    DeferredCall<org.example.eventsourcedentity.CounterApi.IncreaseValue, com.google.protobuf.Empty> increase(org.example.eventsourcedentity.CounterApi.IncreaseValue increaseValue);

    DeferredCall<org.example.eventsourcedentity.CounterApi.DecreaseValue, com.google.protobuf.Empty> decrease(org.example.eventsourcedentity.CounterApi.DecreaseValue decreaseValue);
  }
}
