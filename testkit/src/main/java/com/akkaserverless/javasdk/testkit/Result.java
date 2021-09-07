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

/** @param <R> The type of reply that is expected from invoking a handler */
public final class Result<R> {

  private final R reply;
  private final List<Object> events;
  private final Iterator<Object> eventsIterator;

  public Result(R reply, List<Object> events) {
    this.reply = reply;
    this.events = events;
    this.eventsIterator = events.iterator();
  }

  /**
   * The reply object from the handler. Reply is meant to represent a
   * com.akkaserverless.javasdk.impl.effect.SecondaryEffectImpl type
   */
  // FIXME what about effect().noReply()?
  public R getReply() {
    return reply;
  }

  /** All emitted events. */
  public List<Object> getAllEvents() {
    return events;
  }

  /**
   * Look at the next event and verify that it is of type E or fail if not or if there is no next
   * event.
   *
   * @return The next event if it is of type E, for additional assertions.
   */
  public <E> E getNextEventOfType(Class<E> expectedClass) {
    if (!eventsIterator.hasNext()) throw new NoSuchElementException("No more events found");
    else {
      @SuppressWarnings("unchecked")
      Object next = eventsIterator.next();
      if (expectedClass.isInstance(next)) {
        return (E) next;
      } else {
        throw new NoSuchElementException(
            "expected event type ["
                + expectedClass.getName()
                + "] but found ["
                + next.getClass().getName()
                + "]");
      }
    }
  }
}
