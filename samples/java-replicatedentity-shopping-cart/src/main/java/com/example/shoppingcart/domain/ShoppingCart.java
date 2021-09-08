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
package com.example.shoppingcart.domain;

import com.akkaserverless.javasdk.replicatedentity.ReplicatedCounterMap;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityContext;
import com.example.shoppingcart.ShoppingCartApi;
import com.google.protobuf.Empty;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ShoppingCart extends AbstractShoppingCart {
  @SuppressWarnings("unused")
  private final String entityId;

  public ShoppingCart(ReplicatedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public Effect<Empty> addItem(
      ReplicatedCounterMap<ShoppingCartDomain.Product> cart,
      ShoppingCartApi.AddLineItem addLineItem) {

    if (addLineItem.getQuantity() <= 0) {
      return effects().error("Cannot add negative quantity to item: " + addLineItem.getProductId());
    }

    ShoppingCartDomain.Product product =
        ShoppingCartDomain.Product.newBuilder()
            .setId(addLineItem.getProductId())
            .setName(addLineItem.getName())
            .build();

    cart.increment(product, addLineItem.getQuantity());
    return effects().update(cart).thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> removeItem(
      ReplicatedCounterMap<ShoppingCartDomain.Product> cart,
      ShoppingCartApi.RemoveLineItem removeLineItem) {

    ShoppingCartDomain.Product product =
        ShoppingCartDomain.Product.newBuilder()
            .setId(removeLineItem.getProductId())
            .setName(removeLineItem.getName())
            .build();

    if (!cart.containsKey(product)) {
      return effects().error("Item to remove is not in the cart: " + removeLineItem.getProductId());
    }

    cart.remove(product);
    return effects().update(cart).thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<ShoppingCartApi.Cart> getCart(
      ReplicatedCounterMap<ShoppingCartDomain.Product> cart,
      ShoppingCartApi.GetShoppingCart getShoppingCart) {

    List<ShoppingCartApi.LineItem> allItems =
        cart.keySet().stream()
            .map(
                product ->
                    ShoppingCartApi.LineItem.newBuilder()
                        .setProductId(product.getId())
                        .setName(product.getName())
                        .setQuantity(cart.get(product))
                        .build())
            .sorted(Comparator.comparing(ShoppingCartApi.LineItem::getProductId))
            .collect(Collectors.toList());

    return effects().reply(ShoppingCartApi.Cart.newBuilder().addAllItems(allItems).build());
  }

  @Override
  public Effect<Empty> removeCart(
      ReplicatedCounterMap<ShoppingCartDomain.Product> cart,
      ShoppingCartApi.RemoveShoppingCart removeShoppingCart) {

    return effects().delete().thenReply(Empty.getDefaultInstance());
  }
}
