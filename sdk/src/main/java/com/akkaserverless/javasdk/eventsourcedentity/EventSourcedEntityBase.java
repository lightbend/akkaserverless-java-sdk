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

package com.akkaserverless.javasdk.eventsourcedentity;

import com.akkaserverless.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl;
import com.akkaserverless.javasdk.Metadata;
import com.akkaserverless.javasdk.ServiceCall;
import com.akkaserverless.javasdk.SideEffect;
import com.akkaserverless.javasdk.reply.MessageReply;

import java.util.Collection;
import java.util.function.Function;

// FIXME rename to EventSourcedEntity when the old annotation is removed

/** @param <S> The type of the state for this entity. */
public abstract class EventSourcedEntityBase<S> {

  /**
   * Implement by returning the initial empty state object. This object will be passed into the
   * command and event handlers, until a new state replaces it.
   *
   * <p>Also known as "zero state" or "neutral state".
   *
   * <p><code>null</code> is an allowed value.
   */
  protected abstract S emptyState();

  /**
   * Additional context and meta data for a command handler.
   *
   * <p>It will throw an exception if accessed from constructor or event handler.
   */
  protected CommandContext commandContext() {
    throw new UnsupportedOperationException("Not implemented yet"); // FIXME
  }

  /**
   * Additional context and meta data for an event handler.
   *
   * <p>It will throw an exception if accessed from constructor or command handler.
   */
  protected EventContext eventContext() {
    throw new UnsupportedOperationException("Not implemented yet"); // FIXME
  }

  protected Effect.Builder<S> effects() {
    return new EventSourcedEntityEffectImpl<S>();
  }

  /**
   * A return type to allow returning forwards or failures, and attaching effects to messages.
   *
   * @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the command handler. The effect describes next
     * processing actions, such as emitting events and sending a reply.
     *
     * @param <S> The type of the state for this entity.
     */
    interface Builder<S> {

      OnSuccessBuilder<S> emitEvent(Object event);

      /**
       * Create a message reply.
       *
       * @param message The payload of the reply.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> reply(T message);

      /**
       * Create a message reply.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> reply(T message, Metadata metadata);

      /**
       * Create a forward reply.
       *
       * @param serviceCall The service call representing the forward.
       * @return A forward reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> forward(ServiceCall serviceCall);

      /**
       * Create an error reply.
       *
       * @param description The description of the error.
       * @return An error reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> error(String description);

      /**
       * Create a reply that contains neither a message nor a forward nor an error.
       *
       * <p>This may be useful for emitting effects without sending a message.
       *
       * @return The reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> noReply();
    }

    interface OnSuccessBuilder<S> {

      /**
       * Reply after for example <code>emitEvent</code>.
       *
       * @param replyMessage Function to create the reply message from the new state.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> thenReply(Function<S, T> replyMessage);

      /**
       * Reply after for example <code>emitEvent</code>.
       *
       * @param replyMessage Function to create the reply message from the new state.
       * @param metadata The metadata for the message.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> thenReply(Function<S, T> replyMessage, Metadata metadata);

      /**
       * Create a forward reply after for example <code>emitEvent</code>.
       *
       * @param serviceCall The service call representing the forward.
       * @return A forward reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> thenForward(Function<S, ServiceCall> serviceCall);

      /**
       * Create a reply that contains neither a message nor a forward nor an error.
       *
       * <p>This may be useful for emitting effects without sending a message.
       *
       * @return The reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> thenNoReply();

      /**
       * Attach the given side effect to this reply from the new state.
       *
       * @param sideEffect The effect to attach.
       * @return A new reply with the attached effect.
       */
      OnSuccessBuilder<S> thenAddSideEffect(Function<S, SideEffect> sideEffect);
    }

    /**
     * Attach the given side effects to this reply.
     *
     * @param sideEffects The effects to attach.
     * @return A new reply with the attached effects.
     */
    Effect<T> addSideEffects(Collection<SideEffect> sideEffects);

    /**
     * Attach the given effects to this reply.
     *
     * @param effects The effects to attach.
     * @return A new reply with the attached effects.
     */
    Effect<T> addSideEffects(SideEffect... effects);
  }
}
