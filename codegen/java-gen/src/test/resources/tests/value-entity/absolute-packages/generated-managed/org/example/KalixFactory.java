package org.example;

import kalix.javasdk.Kalix;
import kalix.javasdk.valueentity.ValueEntityContext;
import org.example.domain.Counter;
import org.example.domain.CounterProvider;
import org.example.valueentity.CounterApi;

import java.util.function.Function;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

public final class KalixFactory {

  public static Kalix withComponents(
      Function<ValueEntityContext, Counter> createCounter) {
    Kalix kalix = new Kalix();
    return kalix
      .register(CounterProvider.of(createCounter));
  }
}
