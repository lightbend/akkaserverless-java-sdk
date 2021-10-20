/*
 * Copyright (C) 2009-2021 Lightbend Inc. <http://www.lightbend.com>
 */
package com.example;

// FIXME generate and make accessible in all actions

import com.akkaserverless.javasdk.DeferredCall;
import com.example.actions.CounterStateSubscription;
import com.example.actions.DoubleCounter;
import com.example.domain.Counter;
import com.google.protobuf.Empty;

/**
 * The local components of this service
 *
 * Generated by Akka Serverless, not for user extension
 */
public interface Components {

  CounterStateSubscriptionCalls counterStateSubscription();
  DoubleCounterCalls doubleCounter();
  CounterCalls counter();

  interface CounterCalls {
    // input typed so we can't call it with the wrong value
    // return typed with return value of call so we can match with effects().forward()
    // FIXME we don't really need both type parameters, only return value?
    DeferredCall<CounterApi.IncreaseValue, Empty> increase(CounterApi.IncreaseValue increase);
    // FIXME methods for all
  }
  interface DoubleCounterCalls {
  }
  interface CounterStateSubscriptionCalls {}
}
