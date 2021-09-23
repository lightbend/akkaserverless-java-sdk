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

package com.akkaserverless.javasdk.tck.model.action;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.akkaserverless.javasdk.ServiceCall;
import com.akkaserverless.javasdk.SideEffect;
import com.akkaserverless.javasdk.action.*;
import com.akkaserverless.javasdk.impl.action.ActionEffectImpl;
import com.akkaserverless.tck.model.Action.*;
import com.akkaserverless.tck.model.ActionTwo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ActionTckModelBehavior extends Action {

  public ActionTckModelBehavior(ActionCreationContext creationContext) {}

  public Effect<Response> processUnary(Request request) {
    // Multiple request steps should be combined to give just one response, with subsequent steps
    // taking precedence.
    return response(request.getGroupsList());
  }

  public Effect<Response> processStreamedIn(Source<Request, NotUsed> requests) {
    // All request steps should be combined to produce a single response after the request stream
    // completes.
    CompletionStage<ArrayList<ProcessGroup>> processGroups =
        requests
            .fold(
                new ArrayList<ProcessGroup>(),
                (acc, request) -> {
                  acc.addAll(request.getGroupsList());
                  return acc;
                })
            .runWith(Sink.head(), actionContext().materializer());
    CompletionStage<Effect<Response>> effect = processGroups.thenApply(groups -> response(groups));
    return effects().asyncEffect(effect);
  }

  public Source<Effect<Response>, NotUsed> processStreamedOut(Request request) {
    // The single request may contain multiple grouped steps, each group corresponding to an
    // expected response.
    return Source.from(request.getGroupsList())
        .map(groupList -> response(Collections.singletonList(groupList)));
  }

  public Source<Effect<Response>, NotUsed> processStreamed(Source<Request, NotUsed> requests) {
    // Each request may contain multiple grouped steps, each group corresponding to an expected
    // response.
    return requests
        .mapConcat(request -> request.getGroupsList())
        .map(groupList -> response(Collections.singletonList(groupList)));
  }

  private Effect<Response> response(List<ProcessGroup> groups) {
    Effect<Response> effect = null;
    List<SideEffect> sideEffects = new ArrayList<>();
    // TCK tests expect the logic to be imperative, building something up and then discarding on
    // failure but effect
    // api is not imperative so we have to keep track of failure instead
    boolean didFail = false;
    for (ProcessGroup group : groups) {
      for (ProcessStep step : group.getStepsList()) {
        switch (step.getStepCase()) {
          case REPLY:
            if (!didFail) {
              effect =
                  effects()
                      .reply(
                          Response.newBuilder().setMessage(step.getReply().getMessage()).build());
            }
            break;
          case FORWARD:
            if (!didFail) {
              effect = effects().forward(serviceTwoRequest(step.getForward().getId()));
            }
            break;
          case EFFECT:
            com.akkaserverless.tck.model.Action.SideEffect sideEffect = step.getEffect();
            sideEffects.add(
                com.akkaserverless.javasdk.SideEffect.of(
                    serviceTwoRequest(sideEffect.getId()), sideEffect.getSynchronous()));
            break;
          case FAIL:
            effect = effects().error(step.getFail().getMessage());
            didFail = true;
        }
      }
    }
    if (effect == null) {
      effect = effects().noReply();
    }
    return effect.addSideEffects(sideEffects);
  }

  private ServiceCall serviceTwoRequest(String id) {
    return actionContext()
        .serviceCallFactory()
        .lookup(ActionTwo.name, "Call", OtherRequest.class)
        .createCall(OtherRequest.newBuilder().setId(id).build());
  }
}
