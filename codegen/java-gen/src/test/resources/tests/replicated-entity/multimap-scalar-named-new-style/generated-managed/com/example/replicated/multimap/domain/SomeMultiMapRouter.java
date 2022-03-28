package com.example.replicated.multimap.domain;

import kalix.javasdk.impl.replicatedentity.ReplicatedEntityRouter;
import kalix.javasdk.replicatedentity.CommandContext;
import kalix.javasdk.replicatedentity.ReplicatedEntity;
import kalix.javasdk.replicatedentity.ReplicatedMultiMap;
import com.example.replicated.multimap.SomeMultiMapApi;
import com.google.protobuf.Empty;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * A replicated entity handler that is the glue between the Protobuf service <code>MultiMapService</code>
 * and the command handler methods in the <code>SomeMultiMap</code> class.
 */
public class SomeMultiMapRouter extends ReplicatedEntityRouter<ReplicatedMultiMap<String, Double>, SomeMultiMap> {

  public SomeMultiMapRouter(SomeMultiMap entity) {
    super(entity);
  }

  @Override
  public ReplicatedEntity.Effect<?> handleCommand(
      String commandName, ReplicatedMultiMap<String, Double> data, Object command, CommandContext context) {
    switch (commandName) {

      case "Put":
        return entity().put(data, (SomeMultiMapApi.PutValue) command);

      default:
        throw new ReplicatedEntityRouter.CommandHandlerNotFound(commandName);
    }
  }
}
