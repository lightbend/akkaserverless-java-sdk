/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kalix.springsdk.eventsourced;

import com.google.protobuf.Descriptors;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityOptions;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityProvider;
import kalix.javasdk.impl.MessageCodec;
import kalix.javasdk.impl.eventsourcedentity.EventSourcedEntityRouter;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.common.ForwardHeadersExtractor;
import kalix.springsdk.impl.ComponentDescriptor;
import kalix.springsdk.impl.SpringSdkMessageCodec;
import kalix.springsdk.impl.eventsourcedentity.EventSourcedHandlersExtractor;
import kalix.springsdk.impl.eventsourcedentity.EventSourceEntityHandlers;
import kalix.springsdk.impl.eventsourcedentity.ReflectiveEventSourcedEntityRouter;

import java.util.Optional;
import java.util.function.Function;

public class ReflectiveEventSourcedEntityProvider<S, E extends EventSourcedEntity<S>>
    implements EventSourcedEntityProvider<S, E> {

  private final String entityType;
  private final Function<EventSourcedEntityContext, E> factory;
  private final EventSourcedEntityOptions options;
  private final Descriptors.FileDescriptor fileDescriptor;
  private final Descriptors.ServiceDescriptor serviceDescriptor;
  private final ComponentDescriptor componentDescriptor;

  private final SpringSdkMessageCodec messageCodec;

  private final EventSourceEntityHandlers eventHandlers;

  public static <S, E extends EventSourcedEntity<S>> ReflectiveEventSourcedEntityProvider<S, E> of(
      Class<E> cls,
      SpringSdkMessageCodec messageCodec,
      Function<EventSourcedEntityContext, E> factory) {
    return new ReflectiveEventSourcedEntityProvider<>(
        cls, messageCodec, factory, EventSourcedEntityOptions.defaults());
  }

  public ReflectiveEventSourcedEntityProvider(
      Class<E> entityClass,
      SpringSdkMessageCodec messageCodec,
      Function<EventSourcedEntityContext, E> factory,
      EventSourcedEntityOptions options) {

    EntityType annotation = entityClass.getAnnotation(EntityType.class);
    if (annotation == null)
      throw new IllegalArgumentException(
          "Event Sourced Entity [" + entityClass.getName() + "] is missing '@EntityType' annotation");

    this.eventHandlers = EventSourcedHandlersExtractor.handlersFrom(entityClass, messageCodec);
    if (this.eventHandlers.errors().nonEmpty()) {
      throw new IllegalArgumentException(
          "Event Sourced Entity ["
              + entityClass.getName()
              + "] has event handlers configured incorrectly: "
              + this.eventHandlers.errors());
    }

    this.entityType = annotation.value();
    this.factory = factory;
    this.options = options.withForwardHeaders(ForwardHeadersExtractor.extractFrom(entityClass));
    this.messageCodec = messageCodec;
    this.componentDescriptor = ComponentDescriptor.descriptorFor(entityClass, messageCodec);
    this.fileDescriptor = componentDescriptor.fileDescriptor();
    this.serviceDescriptor = componentDescriptor.serviceDescriptor();
  }

  @Override
  public EventSourcedEntityOptions options() {
    return options;
  }

  @Override
  public Descriptors.ServiceDescriptor serviceDescriptor() {
    return serviceDescriptor;
  }

  @Override
  public String entityType() {
    return entityType;
  }

  @Override
  public EventSourcedEntityRouter<S, E> newRouter(EventSourcedEntityContext context) {
    E entity = factory.apply(context);
    return new ReflectiveEventSourcedEntityRouter<>(
        entity, componentDescriptor.commandHandlers(), eventHandlers.handlers(), messageCodec);
  }

  @Override
  public Descriptors.FileDescriptor[] additionalDescriptors() {
    return new Descriptors.FileDescriptor[] {fileDescriptor};
  }

  @Override
  public Optional<MessageCodec> alternativeCodec() {
    return Optional.of(messageCodec);
  }
}
