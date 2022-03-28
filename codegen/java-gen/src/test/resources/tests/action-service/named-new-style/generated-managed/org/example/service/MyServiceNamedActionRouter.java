package org.example.service;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import kalix.javasdk.action.Action.Effect;
import kalix.javasdk.action.MessageEnvelope;
import kalix.javasdk.impl.action.ActionRouter;
import com.google.protobuf.Empty;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

public class MyServiceNamedActionRouter extends ActionRouter<MyServiceNamedAction> {

  public MyServiceNamedActionRouter(MyServiceNamedAction actionBehavior) {
    super(actionBehavior);
  }

  @Override
  public Effect<?> handleUnary(String commandName, MessageEnvelope<Object> message) {
    switch (commandName) {
      case "simpleMethod":
        return action()
                 .simpleMethod((ServiceOuterClass.MyRequest) message.payload());
      default:
        throw new ActionRouter.HandlerNotFound(commandName);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Source<Effect<?>, NotUsed> handleStreamedOut(String commandName, MessageEnvelope<Object> message) {
    switch (commandName) {
      case "streamedOutputMethod":
        return (Source<Effect<?>, NotUsed>)(Object) action()
                 .streamedOutputMethod((ServiceOuterClass.MyRequest) message.payload());
      default:
        throw new ActionRouter.HandlerNotFound(commandName);
    }
  }

  @Override
  public Effect<?> handleStreamedIn(String commandName, Source<MessageEnvelope<Object>, NotUsed> stream) {
    switch (commandName) {
      case "streamedInputMethod":
        return action()
                 .streamedInputMethod(stream.map(el -> (ServiceOuterClass.MyRequest) el.payload()));
      default:
        throw new ActionRouter.HandlerNotFound(commandName);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Source<Effect<?>, NotUsed> handleStreamed(String commandName, Source<MessageEnvelope<Object>, NotUsed> stream) {
    switch (commandName) {
      case "fullStreamedMethod":
        return (Source<Effect<?>, NotUsed>)(Object) action()
                 .fullStreamedMethod(stream.map(el -> (ServiceOuterClass.MyRequest) el.payload()));
      default:
        throw new ActionRouter.HandlerNotFound(commandName);
    }
  }
}
