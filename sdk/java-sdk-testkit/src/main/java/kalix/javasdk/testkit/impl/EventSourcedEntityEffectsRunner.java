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

package kalix.javasdk.testkit.impl;

import kalix.javasdk.eventsourcedentity.CommandContext;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.testkit.EventSourcedResult;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;
import scala.jdk.javaapi.CollectionConverters;

/** Extended by generated code, not meant for user extension */
public abstract class EventSourcedEntityEffectsRunner<S> {

  private EventSourcedEntity<S> entity;
  private S _state;
  private List<Object> events = new ArrayList();

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S> entity) {
    this.entity = entity;
    this._state = entity.emptyState();
  }

  /** @return The current state of the entity after applying the event */
  protected abstract S handleEvent(S state, Object event);

  /** @return The current state of the entity */
  public S getState() {
    return _state;
  }

  /** @return All events emitted by command handlers of this entity up to now */
  public List<Object> getAllEvents() {
    return events;
  }

  /**
   * creates a command context to run the commands, then creates an event context to run the events,
   * and finally, creates a command context to run the side effects. It cleans each context after
   * each run.
   *
   * @return the result of the side effects
   */
  protected <R> EventSourcedResult<R> interpretEffects(
      Supplier<EventSourcedEntity.Effect<R>> effect) {
    var commandContext = TestKitEventSourcedEntityCommandContext.empty();
    EventSourcedEntity.Effect<R> effectExecuted;
    try {
      entity._internalSetCommandContext(Optional.of(commandContext));
      effectExecuted = effect.get();
      this.events.addAll(EventSourcedResultImpl.eventsOf(effectExecuted));
    } finally {
      entity._internalSetCommandContext(Optional.empty());
    }
    try {
      entity._internalSetEventContext(Optional.of(new TestKitEventSourcedEntityEventContext()));
      for (Object event : EventSourcedResultImpl.eventsOf(effectExecuted)) {
        this._state = handleEvent(this._state, event);
      }
    } finally {
      entity._internalSetEventContext(Optional.empty());
    }
    EventSourcedResult<R> result;
    try {
      entity._internalSetCommandContext(Optional.of(commandContext));
      var secondaryEffect = EventSourcedResultImpl.secondaryEffectOf(effectExecuted, _state);
      result = new EventSourcedResultImpl<R, S>(effectExecuted, _state, secondaryEffect);
    } finally {
      entity._internalSetCommandContext(Optional.empty());
    }
    return result;
  }
}
