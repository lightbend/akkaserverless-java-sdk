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

import com.akkaserverless.javasdk.EntityId;
import com.akkaserverless.javasdk.eventsourcedentity.CommandContext;
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity;
import com.example.shoppingcart.ShoppingCartApi;
import com.google.protobuf.Empty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An event sourced entity.
 */

/**
 * An event sourced entity.
 */
@EventSourcedEntity(entityType = "eventsourced-shopping-cart")
public class ShoppingCartImpl extends ShoppingCartInterface {
  @SuppressWarnings("unused")
  private final String entityId;

  private final Map<String, ShoppingCartApi.LineItem> cart = new LinkedHashMap<>();

  public ShoppingCartImpl(@EntityId String entityId) {
    this.entityId = entityId;
  }

  @Override
  public ShoppingCartDomain.Cart snapshot() {
    return ShoppingCartDomain.Cart.newBuilder()
        .addAllItems(cart.values().stream().map(this::convert).collect(Collectors.toList()))
        .build();
  }

  @Override
  public void handleSnapshot(ShoppingCartDomain.Cart cart) {
    this.cart.clear();
    for (ShoppingCartDomain.LineItem item : cart.getItemsList()) {
      this.cart.put(item.getProductId(), convert(item));
    }
  }

  @Override
  protected Empty addItem(ShoppingCartApi.AddLineItem item, CommandContext ctx) {
    if (item.getQuantity() <= 0) {
      throw ctx.fail("Cannot add negative quantity of to item" + item.getProductId());
    }
    ctx.emit(
        ShoppingCartDomain.ItemAdded.newBuilder()
            .setItem(
                ShoppingCartDomain.LineItem.newBuilder()
                    .setProductId(item.getProductId())
                    .setName(item.getName())
                    .setQuantity(item.getQuantity())
                    .build())
            .build());
    return Empty.getDefaultInstance();
  }

  @Override
  protected Empty removeItem(ShoppingCartApi.RemoveLineItem item, CommandContext ctx) {
    if (!cart.containsKey(item.getProductId())) {
      throw ctx.fail(
          "Cannot remove item " + item.getProductId() + " because it is not in the cart.");
    }
    ctx.emit(ShoppingCartDomain.ItemRemoved.newBuilder().setProductId(item.getProductId()).build());
    return Empty.getDefaultInstance();
  }

  @Override
  protected ShoppingCartApi.Cart getCart(ShoppingCartApi.GetShoppingCart command, CommandContext ctx) {
    return ShoppingCartApi.Cart.newBuilder().addAllItems(cart.values()).build();
  }

  @Override
  public void itemAdded(ShoppingCartDomain.ItemAdded itemAdded) {
    ShoppingCartApi.LineItem item = cart.get(itemAdded.getItem().getProductId());
    if (item == null) {
      item = convert(itemAdded.getItem());
    } else {
      item =
          item.toBuilder()
              .setQuantity(item.getQuantity() + itemAdded.getItem().getQuantity())
              .build();
    }
    cart.put(item.getProductId(), item);
  }

  @Override
  public void itemRemoved(ShoppingCartDomain.ItemRemoved itemRemoved) {
    cart.remove(itemRemoved.getProductId());
  }


  private ShoppingCartApi.LineItem convert(ShoppingCartDomain.LineItem item) {
    return ShoppingCartApi.LineItem.newBuilder()
        .setProductId(item.getProductId())
        .setName(item.getName())
        .setQuantity(item.getQuantity())
        .build();
  }

  private ShoppingCartDomain.LineItem convert(ShoppingCartApi.LineItem item) {
    return ShoppingCartDomain.LineItem.newBuilder()
        .setProductId(item.getProductId())
        .setName(item.getName())
        .setQuantity(item.getQuantity())
        .build();
  }
}