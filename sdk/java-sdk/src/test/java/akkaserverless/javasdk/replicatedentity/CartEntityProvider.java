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

package com.akkaserverless.javasdk.replicatedentity;

import com.akkaserverless.javasdk.impl.Serializer;
import com.example.replicatedentity.shoppingcart.ShoppingCartApi;
import com.example.replicatedentity.shoppingcart.domain.ShoppingCartDomain;
import com.google.protobuf.Descriptors;
import com.google.protobuf.EmptyProto;

import java.util.function.Function;

public class CartEntityProvider
    implements ReplicatedEntityProvider<
        ReplicatedRegisterMap<String, ShoppingCartDomain.LineItem>, CartEntity> {

  private final Function<ReplicatedEntityContext, CartEntity> entityFactory;
  private final ReplicatedEntityOptions options;

  public static CartEntityProvider of(Function<ReplicatedEntityContext, CartEntity> entityFactory) {
    return new CartEntityProvider(entityFactory, ReplicatedEntityOptions.defaults());
  }

  private CartEntityProvider(
      Function<ReplicatedEntityContext, CartEntity> entityFactory,
      ReplicatedEntityOptions options) {
    this.entityFactory = entityFactory;
    this.options = options;
  }

  @Override
  public final ReplicatedEntityOptions options() {
    return options;
  }

  public final CartEntityProvider withOptions(ReplicatedEntityOptions options) {
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
  public final CartEntityRouter newRouter(ReplicatedEntityContext context) {
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
