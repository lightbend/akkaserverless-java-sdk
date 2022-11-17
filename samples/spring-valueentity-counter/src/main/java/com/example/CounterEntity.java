package com.example;

import kalix.javasdk.valueentity.ValueEntity;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.GenerateEntityKey;


import org.springframework.web.bind.annotation.*;

// tag::declarations[]
@EntityType("counter") // <1>
@EntityKey("counter_id") // <2>
public class CounterEntity extends ValueEntity<Integer> { // <3>

  @Override
  public Integer emptyState() { return 0; } // <4>
  // end::declarations[]

  // tag::generateId[]
  @GenerateEntityKey // <1>
  @PostMapping("/counter/{number}")
  public Effect<String> create(@PathVariable Integer number) {
    return effects()
        .updateState(number)
        .thenReply(commandContext().entityId()); // <2>
  }
  // end::generateId[]

  // tag::increase[]

  @PostMapping("/counter/{counter_id}/increase") // <5>
  public Effect<Number> increaseBy(@RequestBody Number increaseBy) {
    int newCounter = currentState() + increaseBy.value(); // <6>
    return effects()
        .updateState(newCounter) // <7>
        .thenReply(new Number(newCounter));
  }
  // end::increase[]

  // tag::behaviour[]
  @PutMapping("/counter/{counter_id}/set") // <1>
  public Effect<Number> set(@RequestBody Number number) {
    int newCounter = number.value();
    return effects()
        .updateState(newCounter) // <2>
        .thenReply(new Number(newCounter)); // <3>
  }

  @PostMapping("/counter/{counter_id}/plusone") // <4>
  public Effect<Number> plusOne() {
    int newCounter = currentState() + 1; // <5>
    return effects()
        .updateState(newCounter) // <6>
        .thenReply(new Number(newCounter));
  }
  // end::behaviour[]

  // tag::delete[]
  @DeleteMapping("/counter/{counter_id}/delete")
  public Effect<String> delete() {
    return effects()
        .deleteState() // <1>
        .thenReply("deleted: " + commandContext().entityId());
  }
  // end::delete[]

  // tag::query[]
  @GetMapping("/counter/{counter_id}") // <1>
  public Effect<Number> get() {
    return effects()
        .reply(new Number(currentState())); // <2>
  }
  // end::query[]
  // tag::close[]

}
// end::close[]