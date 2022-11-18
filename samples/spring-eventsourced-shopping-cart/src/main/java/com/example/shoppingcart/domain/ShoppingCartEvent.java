package com.example.shoppingcart.domain;


// tag::events[]
import kalix.springsdk.annotations.TypeName;
public sealed interface ShoppingCartEvent { // <1>

  @TypeName("item-added") // <2>
  record ItemAdded(ShoppingCart.LineItem item) implements ShoppingCartEvent {}

  @TypeName("item-removed")
  record ItemRemoved(String productId) implements ShoppingCartEvent {}

  @TypeName("checked-out")
  record CheckedOut(int timestamp) implements ShoppingCartEvent {}
}
// end::events[]
