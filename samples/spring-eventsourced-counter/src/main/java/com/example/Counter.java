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

package com.example;

import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static com.example.CounterEvent.ValueIncreased;
import static com.example.CounterEvent.ValueMultiplied;

@EntityKey("id")
@EntityType("counter")
@RequestMapping("/counter/{id}")
public class Counter extends EventSourcedEntity<Integer, CounterEvent> {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public Integer emptyState() {
        return 0;
    }

    @PostMapping("/increase/{value}")
    public Effect<String> increase(@PathVariable Integer value) {
        return effects()
                .emitEvent(new ValueIncreased(value))
                .thenReply(Object::toString);
    }

    @GetMapping
    public Effect<String> get() {
        return effects().reply(currentState().toString());
    }

    @PostMapping("/multiply/{value}")
    public Effect<String> multiply(@PathVariable Integer value) {
        return effects()
                .emitEvent(new ValueMultiplied(value))
                .thenReply(Object::toString);
    }

    @EventHandler
    public Integer handleIncrease(ValueIncreased value) {
        return currentState() + value.value();
    }

    @EventHandler
    public Integer handleMultiply(ValueMultiplied value) {
        return currentState() * value.value();
    }
}

