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

package com.akkaserverless.javasdk.eventsourcedentity;

import com.akkaserverless.javasdk.impl.Serializer;
import com.example.shoppingcart.ShoppingCartApi;
import com.example.shoppingcart.domain.ShoppingCartDomain;
import com.google.protobuf.Descriptors;
import com.google.protobuf.EmptyProto;

import java.util.function.Function;

/** An event sourced entity provider */
public class CartEntityProvider
    implements EventSourcedEntityProvider<ShoppingCartDomain.Cart, CartEntity> {

  private final Function<EventSourcedEntityContext, CartEntity> entityFactory;
  private final EventSourcedEntityOptions options;

  /** Factory method of CartProvider */
  public static CartEntityProvider of(
      Function<EventSourcedEntityContext, CartEntity> entityFactory) {
    return new CartEntityProvider(entityFactory, EventSourcedEntityOptions.defaults());
  }

  private CartEntityProvider(
      Function<EventSourcedEntityContext, CartEntity> entityFactory,
      EventSourcedEntityOptions options) {
    this.entityFactory = entityFactory;
    this.options = options;
  }

  @Override
  public final EventSourcedEntityOptions options() {
    return options;
  }

  public final CartEntityProvider withOptions(EventSourcedEntityOptions options) {
    return new CartEntityProvider(entityFactory, options);
  }

  @Override
  public final Descriptors.ServiceDescriptor serviceDescriptor() {
    return ShoppingCartApi.getDescriptor().findServiceByName("ShoppingCartService");
  }

  @Override
  public final String entityType() {
    return "shopping-cart";
  }

  @Override
  public final CartEntityRouter newRouter(EventSourcedEntityContext context) {
    return new CartEntityRouter(entityFactory.apply(context));
  }

  @Override
  public final Descriptors.FileDescriptor[] additionalDescriptors() {
    return new Descriptors.FileDescriptor[] {
      ShoppingCartApi.getDescriptor(),
      ShoppingCartDomain.getDescriptor(),
      EmptyProto.getDescriptor()
    };
  }

  @Override
  public Serializer serializer() {
    return Serializer.noopSerializer();
  }
}
