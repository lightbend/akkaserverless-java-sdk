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

package kalix.javasdk.workflow;

import akka.Done;
import akka.annotation.ApiMayChange;
import io.grpc.Status;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.Metadata;
import kalix.javasdk.impl.workflow.WorkflowEffectImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public abstract class Workflow<S> {


  private Optional<CommandContext> commandContext = Optional.empty();

  private Optional<S> currentState = Optional.empty();

  private boolean handlingCommands = false;

  /**
   * Implement by returning the initial empty state object. This object will be passed into the
   * command handlers, until a new state replaces it.
   *
   * <p>Also known as "zero state" or "neutral state".
   *
   * <p>The default implementation of this method returns <code>null</code>. It can be overridden to
   * return a more sensible initial state.
   */
  public S emptyState() {
    return null;
  }

  /**
   * Additional context and metadata for a command handler.
   *
   * <p>It will throw an exception if accessed from constructor.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final kalix.javasdk.workflow.CommandContext commandContext() {
    return commandContext.orElseThrow(() -> new IllegalStateException("CommandContext is only available when handling a command."));
  }

  /**
   * INTERNAL API
   */
  public void _internalSetCommandContext(Optional<CommandContext> context) {
    commandContext = context;
  }

  /**
   * INTERNAL API
   */
  public void _internalSetCurrentState(S state) {
    handlingCommands = true;
    currentState = Optional.ofNullable(state);
  }

  /**
   * Returns the state as currently stored by Kalix.
   *
   * <p>Note that modifying the state directly will not update it in storage. To save the state, one
   * must call {{@code effects().updateState()}}.
   *
   * <p>This method can only be called when handling a command. Calling it outside a method (eg: in
   * the constructor) will raise a IllegalStateException exception.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  @ApiMayChange
  protected final S currentState() {
    // user may call this method inside a command handler and get a null because it's legal
    // to have emptyState set to null.
    if (handlingCommands) return currentState.orElse(null);
    else throw new IllegalStateException("Current state is only available when handling a command.");
  }

  public abstract WorkflowDef<S> definition();

  protected final Effect.Builder<S> effects() {
    return WorkflowEffectImpl.apply();
  }

  /**
   * A return type to allow returning forwards or failures, and attaching effects to messages.
   *
   * @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the command handler or as .
     * <p>
     * The effect describes next processing actions, such as updating state, transition to another step
     * and sending a reply.
     *
     * @param <S> The type of the state for this entity.
     */
    interface Builder<S> {

      // TODO: document
      PersistenceEffect<S> updateState(S newState);

      // TODO: document
      TransitionalEffect<Void> waitForInput();

      // TODO: document
      <I> TransitionalEffect<Void> transition(I input, String transitionTo);

      // TODO: document
      TransitionalEffect<Void> end();

      /**
       * Create a message reply.
       *
       * @param replyMessage The payload of the reply.
       * @param <R>          The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> reply(R replyMessage);


      /**
       * Reply after for example <code>updateState</code>.
       *
       * @param message  The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R>      The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> reply(R message, Metadata metadata);

      /**
       * Create an error reply.
       *
       * @param description The description of the error.
       * @param <R>         The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <R> ErrorEffect<R> error(String description);

      /**
       * Create an error reply.
       *
       * @param description The description of the error.
       * @param statusCode  A custom gRPC status code.
       * @param <R>         The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <R> ErrorEffect<R> error(String description, Status.Code statusCode);
    }

    interface ErrorEffect<T> extends Effect<T> {
    }

    interface TransitionalEffect<T> extends Effect<T> {

      /**
       * Reply after for example <code>updateState</code>.
       *
       * @param message The payload of the reply.
       * @param <R>     The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message);

      /**
       * Reply after for example <code>updateState</code>.
       *
       * @param message  The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R>      The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message, Metadata metadata);
    }

    interface PersistenceEffect<T> {

      // TODO: document
      TransitionalEffect<Void> waitForInput();

      // TODO: document
      <I> TransitionalEffect<Void> transition(I input, String transitionTo);

      // TODO: document
      TransitionalEffect<Void> end();
    }


  }

  public static class WorkflowDef<S> {

    final public String name;

    final private List<Step> steps;


    public WorkflowDef(String name, List<Step> steps) {
      this.name = name;
      this.steps = steps;
    }

    public Optional<Step> findByName(String name) {
      return steps.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    public WorkflowDef<S> add(Step step) {
      this.steps.add(step);
      return this;
    }
  }


  public WorkflowDef<S> workflow(String name) {
    var steps = new ArrayList<Step>();
    return new WorkflowDef<>(name, steps);
  }


  public interface Step {
    String name();
  }

  public static class Call<CallInput, DefCallInput, DefCallOutput> implements Step {

    final private String _name;
    final public Function<CallInput, DeferredCall<DefCallInput, DefCallOutput>> callFunc;
    final public Function<DefCallOutput, Effect<Void>> transitionFunc;

    public Call(String name, Function<CallInput,
                DeferredCall<DefCallInput, DefCallOutput>> callFunc,
                Function<DefCallOutput, Effect<Void>> transitionFunc) {
      _name = name;
      this.callFunc = callFunc;
      this.transitionFunc = transitionFunc;
    }


    @Override
    public String name() {
      return this._name;
    }
  }


  public static Workflow.StepBuilder step(String name, String description) {
    return new Workflow.StepBuilder(name, description);
  }

  public static class StepBuilder {

    final private String name;
    final private String description;

    public StepBuilder(String name, String description) {
      this.name = name;
      this.description = description;
    }

    public <Input, DefCallInput, DefCallOutput> CallBuilder<Input, DefCallInput, DefCallOutput> call(Function<Input, DeferredCall<DefCallInput, DefCallOutput>> callFactory) {
      return new CallBuilder<>(name, callFactory);
    }

    public static class CallBuilder<Input, DefCallInput, DefCallOutput> {

      final private String name;

      /* callFactory builds the DeferredCall that will be passed to proxy for execution */
      final private Function<Input, DeferredCall<DefCallInput, DefCallOutput>> callFunc;


      public CallBuilder(String name, Function<Input, DeferredCall<DefCallInput, DefCallOutput>> callFunc) {
        this.name = name;
        this.callFunc = callFunc;
      }

      public Call<Input, DefCallInput, DefCallOutput> andThen(Function<DefCallOutput, Effect<Void>> transitionFunc) {
        return new Call<>(name, callFunc, transitionFunc);
      }

    }

  }

}
