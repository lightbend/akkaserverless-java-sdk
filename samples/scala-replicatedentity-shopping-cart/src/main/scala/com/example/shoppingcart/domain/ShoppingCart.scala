package com.example.shoppingcart.domain

import com.akkaserverless.scalasdk.replicatedentity.ReplicatedCounterMap
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntity
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntityContext
import com.example.shoppingcart
import com.google.protobuf.empty.Empty

// This class was initially generated based on the .proto definition by Akka Serverless tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

/** A replicated entity. */
// tag::class[]
class ShoppingCart(context: ReplicatedEntityContext) extends AbstractShoppingCart { // <1>
  // end::class[]

  /** Command handler for "AddItem". */
  // tag::addItem[]
  def addItem(
      cart: ReplicatedCounterMap[Product],
      addLineItem: shoppingcart.AddLineItem): ReplicatedEntity.Effect[Empty] = {
    if (addLineItem.quantity <= 0) { // <1>
      effects.error(s"Quantity for item ${addLineItem.productId} must be greater than zero.")
    } else {
      val product = Product(addLineItem.productId, addLineItem.name) // <2>
      val updatedCart = cart.increment(product, addLineItem.quantity) // <3>

      effects
        .update(updatedCart) // <4>
        .thenReply(Empty.defaultInstance) // <5>
    }
  }
  // end::addItem[]

  /** Command handler for "RemoveItem". */
  def removeItem(
      cart: ReplicatedCounterMap[Product], // <1>
      removeLineItem: shoppingcart.RemoveLineItem): ReplicatedEntity.Effect[Empty] = {
    val product = Product(removeLineItem.productId, removeLineItem.name) // <2>

    if (!cart.contains(product)) {
      effects.error(s"Item to remove is not in the cart: ${removeLineItem.productId}")
    } else {
      effects
        .update(cart.remove(product))
        .thenReply(Empty.defaultInstance)
    }
  }

  /** Command handler for "GetCart". */
  // tag::getCart[]
  def getCart(
      cart: ReplicatedCounterMap[Product], // <1>
      getShoppingCart: shoppingcart.GetShoppingCart): ReplicatedEntity.Effect[shoppingcart.Cart] = {

    val allItems =
      cart.keySet
        .map { product =>
          val quantity = cart.get(product).getOrElse(0L)
          shoppingcart.LineItem(product.id, product.name, quantity)
        }
        .toSeq
        .sortBy(_.productId)

    val apiCart = shoppingcart.Cart(allItems) // <2>
    effects.reply(apiCart)
  }
  // end::getCart[]

  /** Command handler for "RemoveCart". */
  // tag::removeCart[]
  def removeCart(
      cart: ReplicatedCounterMap[Product],
      removeShoppingCart: shoppingcart.RemoveShoppingCart): ReplicatedEntity.Effect[Empty] =
    effects.delete // <1>
      .thenReply(Empty.defaultInstance)
  // end::removeCart[]

}
