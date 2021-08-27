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

package com.akkaserverless.javasdk;

import com.akkaserverless.javasdk.impl.reply.ErrorReplyImpl;
import com.akkaserverless.javasdk.impl.reply.ForwardReplyImpl;
import com.akkaserverless.javasdk.impl.reply.MessageReplyImpl;
import com.akkaserverless.javasdk.impl.reply.NoReply;
import com.akkaserverless.javasdk.reply.ErrorReply;
import com.akkaserverless.javasdk.reply.ForwardReply;
import com.akkaserverless.javasdk.reply.MessageReply;

import java.util.Collection;
import java.util.function.Function;

/**
 * A return type to allow returning forwards or failures, and attaching effects to messages.
 *
 * @param <T> The type of the message that must be returned by this call.
 */
public interface Reply<T> {
  /**
   * Whether this reply is empty: does not have a message, forward, or error.
   *
   * @return Whether the reply is empty.
   */
  boolean isEmpty();

  /**
   * The effects attached to this reply.
   *
   * @return The effects.
   */
  Collection<SideEffect> sideEffects();

  /**
   * Attach the given side effects to this reply.
   *
   * @param sideEffects The effects to attach.
   * @return A new reply with the attached effects.
   */
  Reply<T> addSideEffects(Collection<SideEffect> sideEffects);

  /**
   * Attach the given effects to this reply.
   *
   * @param effects The effects to attach.
   * @return A new reply with the attached effects.
   */
  Reply<T> addSideEffects(SideEffect... effects);

  /**
   * Create a message reply.
   *
   * @param payload The payload of the reply.
   * @return A message reply.
   */
  static <T> MessageReply<T> message(T payload) {
    return message(payload, Metadata.EMPTY);
  }

  /**
   * Create a message reply.
   *
   * @param payload The payload of the reply.
   * @param metadata The metadata for the message.
   * @return A message reply.
   */
  static <T> MessageReply<T> message(T payload, Metadata metadata) {
    return new MessageReplyImpl<>(payload, metadata);
  }

  /**
   * Create a forward reply.
   *
   * @param serviceCall The service call representing the forward.
   * @return A forward reply.
   */
  static <T> ForwardReply<T> forward(ServiceCall serviceCall) {
    return new ForwardReplyImpl<>(serviceCall);
  }

  /**
   * Create a failure reply.
   *
   * @param description The description of the failure.
   * @return A failure reply.
   */
  static <T> ErrorReply<T> failure(String description) {
    return new ErrorReplyImpl<>(description);
  }

  /**
   * Create a reply that contains neither a message nor a forward nor a failure.
   *
   * <p>This may be useful for emitting effects without sending a message.
   *
   * @return The reply.
   */
  static <T> Reply<T> noReply() {
    return NoReply.apply();
  }

  public default Reply<Object> mapToObject() {
    return (Reply<Object>) this;
  }
}
