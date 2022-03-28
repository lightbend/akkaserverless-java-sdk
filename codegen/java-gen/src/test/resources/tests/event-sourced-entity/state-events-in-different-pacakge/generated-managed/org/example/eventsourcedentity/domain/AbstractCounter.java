package org.example.eventsourcedentity.domain;

import com.google.protobuf.Empty;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import org.example.Components;
import org.example.ComponentsImpl;
import org.example.eventsourcedentity.CounterApi;
import org.example.eventsourcedentity.events.OuterCounterEvents;
import org.example.eventsourcedentity.state.OuterCounterState;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

public abstract class AbstractCounter extends EventSourcedEntity<OuterCounterState.CounterState> {

  protected final Components components() {
    return new ComponentsImpl(commandContext());
  }

  public abstract Effect<Empty> increase(OuterCounterState.CounterState currentState, CounterApi.IncreaseValue increaseValue);

  public abstract Effect<Empty> decrease(OuterCounterState.CounterState currentState, CounterApi.DecreaseValue decreaseValue);

  public abstract OuterCounterState.CounterState increased(OuterCounterState.CounterState currentState, OuterCounterEvents.Increased increased);

  public abstract OuterCounterState.CounterState decreased(OuterCounterState.CounterState currentState, OuterCounterEvents.Decreased decreased);

}