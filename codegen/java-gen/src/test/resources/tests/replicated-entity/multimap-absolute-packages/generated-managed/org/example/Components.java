package org.example;

import com.akkaserverless.javasdk.DeferredCall;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * Not intended for user extension, provided through generated implementation
 */
public interface Components {
  SomeMultiMapCalls someMultiMap();

  interface SomeMultiMapCalls {
    DeferredCall<com.example.replicated.multimap.SomeMultiMapApi.PutValue, com.google.protobuf.Empty> put(com.example.replicated.multimap.SomeMultiMapApi.PutValue putValue);
  }
}
