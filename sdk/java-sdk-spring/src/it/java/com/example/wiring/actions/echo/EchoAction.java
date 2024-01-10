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

package com.example.wiring.actions.echo;

import kalix.javasdk.Metadata;
import kalix.javasdk.StatusCode;
import kalix.javasdk.action.Action;
import kalix.javasdk.action.ActionCreationContext;
import kalix.javasdk.client.ComponentClient;
import kalix.spring.KalixClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class EchoAction extends Action {

  private Parrot parrot;
  private ActionCreationContext ctx;
  private final KalixClient kalixClient;
  private final ComponentClient componentClient;

  public EchoAction(Parrot parrot, ActionCreationContext ctx, KalixClient kalixClient, ComponentClient componentClient) {
    this.parrot = parrot;
    this.ctx = ctx;
    this.kalixClient = kalixClient;
    this.componentClient = componentClient;
  }

  @GetMapping("/echo/message/{msg_value}")
  public Effect<Message> stringMessage(@PathVariable("msg_value") String msg) {
    String response = this.parrot.repeat(msg);
    return effects().reply(new Message(response));
  }

  @GetMapping("/echo/message")
  public Effect<Message> stringMessageFromParam(@RequestParam String msg) {
    return stringMessage(msg);
  }

  @PostMapping("/echo/message/forward")
  public Effect<Message> stringMessageFromParamFw(@RequestParam String msg) {
    var result = kalixClient.get("/echo/message?msg=" + URLEncoder.encode(msg, StandardCharsets.UTF_8), Message.class);
    return effects().forward(result);
  }

  @PostMapping("/echo/message/forward")
  public Effect<Message> stringMessageFromParamFwTyped(@RequestParam String msg) {
    var result = componentClient.forAction().call(EchoAction::stringMessageFromParam).params(msg);
    return effects().forward(result);
  }

  @PostMapping("/echo/message/concat")
  public Effect<Message> stringMessageConcatRequestBody(@RequestBody List<Message> messages) {
    var allMessages = messages.stream().map(m -> m.text).collect(Collectors.joining("|"));
    return effects().reply(new Message(allMessages));
  }

  @PostMapping("/echo/message/concat/{separator}")
  public Effect<Message> stringMessageConcatRequestBodyWithSeparator(@PathVariable String separator, @RequestBody List<Message> messages ) {
    var allMessages = messages.stream().map(m -> m.text).collect(Collectors.joining(separator));
    return effects().reply(new Message(allMessages));
  }

  @GetMapping("/echo/repeat/{msg}/times/{times}")
  public Flux<Effect<Message>> stringMessageRepeat(
      @PathVariable String msg, @PathVariable Integer times) {
    return Flux.range(1, times)
        // add an async boundary just to have some thread switching
        .flatMap(
            i -> Mono.fromCompletionStage(CompletableFuture.supplyAsync(() -> parrot.repeat(msg))))
        .map(m -> effects().reply(new Message(m)));
  }

  @PostMapping("/echo/message/customCode/{msg}")
  public Effect<Message> stringMessageCustomCode(@PathVariable String msg) {
    String response = this.parrot.repeat(msg);
    return effects().reply(new Message(response),
        Metadata.EMPTY.withStatusCode(StatusCode.Success.ACCEPTED));
  }
}
