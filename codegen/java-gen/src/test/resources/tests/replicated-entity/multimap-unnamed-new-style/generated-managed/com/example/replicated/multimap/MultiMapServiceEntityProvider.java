package com.example.replicated.multimap;

import com.akkaserverless.javasdk.impl.Serializer;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityContext;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityOptions;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityProvider;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedMultiMap;
import com.example.replicated.multimap.domain.SomeMultiMapDomain;
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
public class MultiMapServiceEntityProvider implements ReplicatedEntityProvider<ReplicatedMultiMap<SomeMultiMapDomain.SomeKey, SomeMultiMapDomain.SomeValue>, MultiMapServiceEntity> {

  private final Function<ReplicatedEntityContext, MultiMapServiceEntity> entityFactory;
  private final ReplicatedEntityOptions options;

  /** Factory method of MultiMapServiceEntityProvider */
  public static MultiMapServiceEntityProvider of(Function<ReplicatedEntityContext, MultiMapServiceEntity> entityFactory) {
    return new MultiMapServiceEntityProvider(entityFactory, ReplicatedEntityOptions.defaults());
  }

  private MultiMapServiceEntityProvider(
      Function<ReplicatedEntityContext, MultiMapServiceEntity> entityFactory,
      ReplicatedEntityOptions options) {
    this.entityFactory = entityFactory;
    this.options = options;
  }

  @Override
  public final ReplicatedEntityOptions options() {
    return options;
  }

  public final MultiMapServiceEntityProvider withOptions(ReplicatedEntityOptions options) {
    return new MultiMapServiceEntityProvider(entityFactory, options);
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
  public final MultiMapServiceEntityRouter newRouter(ReplicatedEntityContext context) {
    return new MultiMapServiceEntityRouter(entityFactory.apply(context));
  }

  @Override
  public final Descriptors.FileDescriptor[] additionalDescriptors() {
    return new Descriptors.FileDescriptor[] {
      EmptyProto.getDescriptor(),
      SomeMultiMapApi.getDescriptor(),
      SomeMultiMapDomain.getDescriptor()
    };
  }
  
  @Override
  public Serializer serializer() { 
    return Serializer.noopSerializer();
  }
}
