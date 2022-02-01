package org.example.eventsourcedentity;

import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityOptions;
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityProvider;
import com.akkaserverless.javasdk.impl.Serializer;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.EmptyProto;

import java.util.function.Function;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * An event sourced entity provider that defines how to register and create the entity for
 * the Protobuf service <code>CounterService</code>.
 *
 * Should be used with the <code>register</code> method in {@link com.akkaserverless.javasdk.AkkaServerless}.
 */
public class CounterProvider implements EventSourcedEntityProvider<CounterDomain.CounterState, Counter> {

  private final Function<EventSourcedEntityContext, Counter> entityFactory;
  private final EventSourcedEntityOptions options;

  /** Factory method of CounterProvider */
  public static CounterProvider of(Function<EventSourcedEntityContext, Counter> entityFactory) {
    return new CounterProvider(entityFactory, EventSourcedEntityOptions.defaults());
  }

  private CounterProvider(
      Function<EventSourcedEntityContext, Counter> entityFactory,
      EventSourcedEntityOptions options) {
    this.entityFactory = entityFactory;
    this.options = options;
  }

  @Override
  public final EventSourcedEntityOptions options() {
    return options;
  }

  public final CounterProvider withOptions(EventSourcedEntityOptions options) {
    return new CounterProvider(entityFactory, options);
  }

  @Override
  public final Descriptors.ServiceDescriptor serviceDescriptor() {
    return CounterApi.getDescriptor().findServiceByName("CounterService");
  }

  @Override
  public final String entityType() {
    return "counter";
  }

  @Override
  public final CounterRouter newRouter(EventSourcedEntityContext context) {
    return new CounterRouter(entityFactory.apply(context));
  }

  @Override
  public final Descriptors.FileDescriptor[] additionalDescriptors() {
    return new Descriptors.FileDescriptor[] {
      CounterApi.getDescriptor(),
      CounterDomain.getDescriptor(),
      EmptyProto.getDescriptor()
    };
  }
  
  @Override
  public Serializer serializer() { 
    return Serializer.noopSerializer();
  }
}
