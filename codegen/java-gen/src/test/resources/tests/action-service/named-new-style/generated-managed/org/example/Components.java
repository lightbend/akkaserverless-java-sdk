package org.example;

import com.akkaserverless.javasdk.DeferredCall;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * Not intended for user extension, provided through generated implementation
 */
public interface Components {
  MyServiceNamedActionCalls myServiceNamedAction();

  interface MyServiceNamedActionCalls {
    DeferredCall<org.example.service.ServiceOuterClass.MyRequest, com.google.protobuf.Empty> simpleMethod(org.example.service.ServiceOuterClass.MyRequest myRequest);
  }
}
