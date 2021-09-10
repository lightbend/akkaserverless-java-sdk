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

package com.akkaserverless.javasdk.testkit;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Represents the result of an EventSourcedEntity handling a command when run in through the
 * testkit.
 *
 * @param <R> The type of reply that is expected from invoking command handler
 */
public interface EventSourcedResult<R> {

  /** @return true if the call had an effect with a reply, false if not */
  boolean isReply();

  /**
   * The reply object from the handler if there was one. If the call had an effect without any reply
   * an exception is thrown
   */
  R getReply();

  /** @return true if the call was forwarded, false if not */
  boolean isForward();

  /**
   * An object with details about the forward. If the result was not a forward an exception is
   * thrown
   */
  ServiceCallDetails<R> getForward();

  /** @return true if the call was an error, false if not */
  boolean isError();

  /** The error description. If the result was not an error an exception is thrown */
  String getError();

  /** @return true if the call had a noReply effect, false if not */
  boolean isNoReply();

  /**
   * @return The updated state. If the state was not updated (no events emitted) an exeption is
   *     thrown
   */
  Object getUpdatedState();

  boolean didEmitEvents();

  /** @return All the events that were emitted by handling this command. */
  List<Object> getAllEvents();

  /**
   * Look at the next event and verify that it is of type E or fail if not or if there is no next
   * event. If successful this consumes the event, so that the next call to this method looks at the
   * next event from here.
   *
   * @return The next event if it is of type E, for additional assertions.
   */
  <E> E getNextEventOfType(Class<E> expectedClass);
}
