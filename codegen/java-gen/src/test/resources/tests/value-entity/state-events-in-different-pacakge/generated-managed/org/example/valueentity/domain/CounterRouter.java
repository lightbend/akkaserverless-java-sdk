package org.example.valueentity.domain;

import com.google.protobuf.Empty;
import kalix.javasdk.impl.valueentity.ValueEntityRouter;
import kalix.javasdk.valueentity.CommandContext;
import kalix.javasdk.valueentity.ValueEntity;
import org.example.valueentity.CounterApi;
import org.example.valueentity.state.OuterCounterState;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * A value entity handler that is the glue between the Protobuf service <code>CounterService</code>
 * and the command handler methods in the <code>Counter</code> class.
 */
public class CounterRouter extends ValueEntityRouter<OuterCounterState.CounterState, Counter> {

  public CounterRouter(Counter entity) {
    super(entity);
  }

  @Override
  public ValueEntity.Effect<?> handleCommand(
      String commandName, OuterCounterState.CounterState state, Object command, CommandContext context) {
    switch (commandName) {

      case "Increase":
        return entity().increase(state, (CounterApi.IncreaseValue) command);

      case "Decrease":
        return entity().decrease(state, (CounterApi.DecreaseValue) command);

      default:
        throw new ValueEntityRouter.CommandHandlerNotFound(commandName);
    }
  }
}
