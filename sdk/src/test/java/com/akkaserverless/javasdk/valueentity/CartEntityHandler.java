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

package com.akkaserverless.javasdk.valueentity;

import com.akkaserverless.javasdk.impl.valueentity.ValueEntityHandler;
import com.example.valueentity.shoppingcart.ShoppingCartApi;
import com.example.valueentity.shoppingcart.domain.ShoppingCartDomain;

/** A value entity handler */
public class CartEntityHandler extends ValueEntityHandler<ShoppingCartDomain.Cart, CartEntity> {

  public CartEntityHandler(CartEntity entity) {
    super(entity);
  }

  @Override
  public ValueEntityBase.Effect<?> handleCommand(
      String commandName, ShoppingCartDomain.Cart state, Object command, CommandContext context) {
    switch (commandName) {
      case "AddItem":
        return entity().addItem(state, (ShoppingCartApi.AddLineItem) command);
      case "RemoveItem":
        return entity().removeItem(state, (ShoppingCartApi.RemoveLineItem) command);
      case "GetCart":
        return entity().getCart(state, (ShoppingCartApi.GetShoppingCart) command);
      case "RemoveCart":
        return entity().removeCart(state, (ShoppingCartApi.RemoveShoppingCart) command);
      default:
        throw new ValueEntityHandler.CommandHandlerNotFound(commandName);
    }
  }
}
