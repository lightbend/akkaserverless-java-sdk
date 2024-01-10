/*
 * Copyright 2024 Lightbend Inc.
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

package com.example.wiring.workflowentities;

import com.example.wiring.actions.echo.Message;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.workflow.Workflow;
import kalix.spring.KalixClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static kalix.javasdk.workflow.Workflow.RecoverStrategy.maxRetries;

@TypeId("workflow-with-step-timeout")
@Id("workflowId")
@RequestMapping("/workflow-with-step-timeout/{workflowId}")
public class WorkflowWithStepTimeout extends Workflow<FailingCounterState> {

  private Logger logger = LoggerFactory.getLogger(getClass());
  private final String counterStepName = "counter";
  private final String counterFailoverStepName = "counter-failover";

  public Executor delayedExecutor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);

  @Override
  public WorkflowDef<FailingCounterState> definition() {
    var counterInc =
        step(counterStepName)
            .asyncCall(() -> {
              logger.info("Running");
              return CompletableFuture.supplyAsync(() -> "produces time out", delayedExecutor);
            })
            .andThen(String.class, __ -> effects().transitionTo(counterFailoverStepName))
            .timeout(ofMillis(20));

    var counterIncFailover =
        step(counterFailoverStepName)
            .asyncCall(() -> CompletableFuture.completedStage("nothing"))
            .andThen(String.class, __ -> {
              var updatedState = currentState().inc();
              if (updatedState.value() == 2) {
                return effects()
                    .updateState(updatedState.asFinished())
                    .end();
              } else {
                return effects()
                    .updateState(updatedState)
                    .transitionTo(counterStepName);
              }
            });


    return workflow()
        .timeout(ofSeconds(8))
        .defaultStepTimeout(ofMillis(20))
        .addStep(counterInc, maxRetries(1).failoverTo(counterFailoverStepName))
        .addStep(counterIncFailover);
  }

  @PutMapping("/{counterId}")
  public Effect<Message> startFailingCounter(@PathVariable String counterId) {
    return effects()
        .updateState(new FailingCounterState(counterId, 0, false))
        .transitionTo(counterStepName)
        .thenReply(new Message("workflow started"));
  }

  @GetMapping
  public Effect<FailingCounterState> get() {
    return effects().reply(currentState());
  }
}
