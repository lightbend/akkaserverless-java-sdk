package org.example.service;

import com.google.protobuf.Empty;
import kalix.javasdk.action.ActionCreationContext;

// This class was initially generated based on the .proto definition by Kalix tooling.
// This is the implementation for the Action Service described in your some_action.proto file.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

public class SomeServiceAction extends AbstractSomeServiceAction {

  public SomeServiceAction(ActionCreationContext creationContext) {}

  @Override
  public Effect<Empty> simpleMethod(SomeServiceOuterClass.SomeRequest someRequest) {
    throw new RuntimeException("The command handler for `simpleMethod` is not implemented, yet");
  }
}
