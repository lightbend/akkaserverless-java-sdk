package org.example;

import kalix.javasdk.Kalix;
import kalix.javasdk.action.ActionCreationContext;
import org.example.service.MyServiceAction;
import org.example.service.MyServiceActionProvider;
import org.example.service.ServiceOuterClass;

import java.util.function.Function;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

public final class KalixFactory {

  public static Kalix withComponents(
      Function<ActionCreationContext, MyServiceAction> createMyServiceAction) {
    Kalix kalix = new Kalix();
    return kalix
      .register(MyServiceActionProvider.of(createMyServiceAction));
  }
}
