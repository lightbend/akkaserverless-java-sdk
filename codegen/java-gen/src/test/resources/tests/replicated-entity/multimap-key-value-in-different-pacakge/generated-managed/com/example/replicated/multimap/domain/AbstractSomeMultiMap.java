package com.example.replicated.multimap.domain;

import com.akkaserverless.javasdk.replicatedentity.ReplicatedMultiMap;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedMultiMapEntity;
import com.example.replicated.multimap.SomeMultiMapApi;
import com.example.replicated.multimap.domain.key.SomeMultiMapDomainKey;
import com.example.replicated.multimap.domain.value.SomeMultiMapDomainValue;
import com.google.protobuf.Empty;
import org.example.Components;
import org.example.ComponentsImpl;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/** A replicated entity. */
public abstract class AbstractSomeMultiMap extends ReplicatedMultiMapEntity<SomeMultiMapDomainKey.SomeKey, SomeMultiMapDomainValue.SomeValue> {

  protected final Components components() {
    return new ComponentsImpl(commandContext());
  }

  /** Command handler for "Put". */
  public abstract Effect<Empty> put(ReplicatedMultiMap<SomeMultiMapDomainKey.SomeKey, SomeMultiMapDomainValue.SomeValue> currentData, SomeMultiMapApi.PutValue putValue);

}
