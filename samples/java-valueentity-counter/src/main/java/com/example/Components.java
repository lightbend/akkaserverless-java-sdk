/*
 * Copyright (C) 2009-2021 Lightbend Inc. <http://www.lightbend.com>
 */
package com.example;

// FIXME generate and make accessible in all actions

import com.example.actions.CounterStateSubscription;
import com.example.actions.DoubleCounter;
import com.example.domain.Counter;

/**
 * The local components of this service
 *
 * Generated by Akka Serverless, not for user extension
 */
public interface Components {
  // gRPC service interfaces
  CounterStateSubscription counterStateSubscription();
  DoubleCounter doubleCounter();
  CounterService counter();
}
