package com.example.domain;

import io.grpc.Status;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.valueentity.ValueEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import org.springframework.web.bind.annotation.*;

// tag::order[]
@EntityKey("id")
@EntityType("order")
@RequestMapping("/order/{id}")
public class OrderEntity extends ValueEntity<Order> {

  private final String entityId;

  public OrderEntity(ValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public Order emptyState() {
    return new Order(entityId, false, false, "", 0);
  }

  @PutMapping("/place")
  public Effect<Order> placeOrder(@PathVariable String id,
                                  @RequestBody OrderRequest orderRequest) { // <1>
    var newOrder = new Order(
        id,
        false,
        true, // <2>
        orderRequest.item(),
        orderRequest.quantity());
    return effects()
            .updateState(newOrder)
            .thenReply(newOrder);
  }

  @PostMapping("/confirm")
  public Effect<String> confirm(@PathVariable String id) {
    if (currentState().placed()) { // <3>
      return effects()
          .updateState(currentState().confirm())
          .thenReply("Ok");
    } else {
      return effects().error(
        "No order found for '" + id + "'",
          Status.Code.NOT_FOUND); // <4>
    }
  }

  @PostMapping("/cancel")
  public Effect<String> cancel(@PathVariable String id) {
    if (!currentState().placed()) {
      return effects().error(
          "No order found for " + id,
          Status.Code.NOT_FOUND); // <5>
    } else if (currentState().confirmed()) {
      return effects().error(
          "Cannot cancel an already confirmed order",
          Status.Code.INVALID_ARGUMENT); // <6>
    } else {
      return effects().updateState(emptyState())
          .thenReply("Ok"); // <7>
    }
  }
  // end::order[]

  @GetMapping
  public Effect<OrderStatus> status(@PathVariable String id) {
    if (currentState().placed()) {
      var orderStatus = new OrderStatus(id, currentState().item(), currentState().quantity(), currentState().confirmed());
      return effects().reply(orderStatus);
    } else {
      return effects().error(
          "No order found for '" + id + "'",
          Status.Code.NOT_FOUND);
    }
  }
// tag::order[]
}
// end::order[]
