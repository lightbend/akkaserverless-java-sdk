package com.example.replicated.multimap.domain;

import com.akkaserverless.javasdk.impl.Serializer;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityContext;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityOptions;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityProvider;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedMultiMap;
import com.example.replicated.multimap.SomeMultiMapApi;
import com.example.replicated.multimap.domain.key.SomeMultiMapDomainKey;
import com.example.replicated.multimap.domain.value.SomeMultiMapDomainValue;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.EmptyProto;

import java.util.function.Function;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * A replicated entity provider that defines how to register and create the entity for
 * the Protobuf service <code>MultiMapService</code>.
 *
 * Should be used with the <code>register</code> method in {@link com.akkaserverless.javasdk.AkkaServerless}.
 */
public class SomeMultiMapProvider implements ReplicatedEntityProvider<ReplicatedMultiMap<SomeMultiMapDomainKey.SomeKey, SomeMultiMapDomainValue.SomeValue>, SomeMultiMap> {

  private final Function<ReplicatedEntityContext, SomeMultiMap> entityFactory;
  private final ReplicatedEntityOptions options;

  /** Factory method of SomeMultiMapProvider */
  public static SomeMultiMapProvider of(Function<ReplicatedEntityContext, SomeMultiMap> entityFactory) {
    return new SomeMultiMapProvider(entityFactory, ReplicatedEntityOptions.defaults());
  }

  private SomeMultiMapProvider(
      Function<ReplicatedEntityContext, SomeMultiMap> entityFactory,
      ReplicatedEntityOptions options) {
    this.entityFactory = entityFactory;
    this.options = options;
  }

  @Override
  public final ReplicatedEntityOptions options() {
    return options;
  }

  public final SomeMultiMapProvider withOptions(ReplicatedEntityOptions options) {
    return new SomeMultiMapProvider(entityFactory, options);
  }

  @Override
  public final Descriptors.ServiceDescriptor serviceDescriptor() {
    return SomeMultiMapApi.getDescriptor().findServiceByName("MultiMapService");
  }

  @Override
  public final String entityType() {
    return "some-multi-map";
  }

  @Override
  public final SomeMultiMapRouter newRouter(ReplicatedEntityContext context) {
    return new SomeMultiMapRouter(entityFactory.apply(context));
  }

  @Override
  public final Descriptors.FileDescriptor[] additionalDescriptors() {
    return new Descriptors.FileDescriptor[] {
      EmptyProto.getDescriptor(),
      SomeMultiMapApi.getDescriptor(),
      SomeMultiMapDomainKey.getDescriptor(),
      SomeMultiMapDomainValue.getDescriptor()
    };
  }
  
  @Override
  public Serializer serializer() { 
    return Serializer.noopSerializer();
  }
}
