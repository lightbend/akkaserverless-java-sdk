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

package com.example.wiring;

import com.example.Main;
import com.example.wiring.actions.echo.Message;
import com.example.wiring.valueentities.user.User;
import com.example.wiring.views.UserWithVersion;
import kalix.springsdk.KalixConfigurationTest;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Main.class)
@Import(KalixConfigurationTest.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
public class SpringSdkWiringIntegrationTest {

  @Autowired private WebClient webClient;

  private Duration timeout = Duration.of(10, SECONDS);

  @Test
  public void verifyEchoActionWiring() {

    Message response =
        webClient
            .get()
            .uri("/echo/message/abc")
            .retrieve()
            .bodyToMono(Message.class)
            .block(timeout);

    Assertions.assertEquals("Parrot says: 'abc'", response.text);
  }

  @Test
  public void verifyStreamActions() {

    List<Message> messageList =
        webClient
            .get()
            .uri("/echo/repeat/abc/times/3")
            .retrieve()
            .bodyToFlux(Message.class)
            .toStream()
            .collect(Collectors.toList());

    Assertions.assertEquals(3, messageList.size());
  }

  @Test
  public void verifyCounterEventSourcedWiring() {

    String counterIncrease =
        webClient
            .post()
            .uri("/counter/hello/increase/10")
            .retrieve()
            .bodyToMono(String.class)
            .block(timeout);

    Assertions.assertEquals("\"10\"", counterIncrease);

    String counterMultiply =
        webClient
            .post()
            .uri("/counter/hello/multiply/20")
            .retrieve()
            .bodyToMono(String.class)
            .block(timeout);

    Assertions.assertEquals("\"200\"", counterMultiply);

    String counterGet =
        webClient.get().uri("/counter/hello").retrieve().bodyToMono(String.class).block(timeout);

    Assertions.assertEquals("\"200\"", counterGet);
  }

  @Test
  public void verifyTransformedUserViewWiring() throws InterruptedException {

    User u1 = new User("john@doe.com", "JohnDoe");
    String userCreation =
        webClient
            .post()
            .uri("/user/JohnDoe/" + u1.email + "/" + u1.name)
            .retrieve()
            .bodyToMono(String.class)
            .block(timeout);
    Assertions.assertEquals("\"Ok\"", userCreation);

    String userUpdate =
        webClient
            .post()
            .uri("/user/JohnDoe/" + u1.email + "/JohnDoeJr")
            .retrieve()
            .bodyToMono(String.class)
            .block(timeout);
    Assertions.assertEquals("\"Ok\"", userUpdate);

    // the view is eventually updated
    await()
        .atMost(15, TimeUnit.of(SECONDS))
        .until(
            () -> {
              try {
                var userView =
                    webClient
                        .get()
                        .uri("/users/by-email/" + u1.email)
                        .retrieve()
                        .bodyToMono(UserWithVersion.class)
                        .block(timeout);
                return userView != null && 2 == userView.version;
              } catch (Exception e) {
                return false;
              }
            });
  }

  @Test
  public void verifyFindUsersByEmail() {

    ResponseEntity<String> response =
        webClient
            .post()
            .uri("/user/jane/jane.example.com/jane")
            .retrieve()
            .toEntity(String.class)
            .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());

    // the view is eventually updated
    await()
        .atMost(20, TimeUnit.SECONDS)
        .until(
            () ->
                webClient
                    .get()
                    .uri("/users/by_email/jane.example.com")
                    .retrieve()
                    .bodyToMono(User.class)
                    .block()
                    .email,
            new IsEqual("jane.example.com"));
  }

  @Test
  public void verifyFindUsersByNameStreaming() {

    { // joe 1
      ResponseEntity<String> response =
          webClient
              .post()
              .uri("/user/user1/joe1.example.com/joe")
              .retrieve()
              .toEntity(String.class)
              .block(timeout);

      Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    { // joe 2
      ResponseEntity<String> response =
          webClient
              .post()
              .uri("/user/user2/joe2.example.com/joe")
              .retrieve()
              .toEntity(String.class)
              .block(timeout);

      Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // the view is eventually updated
    await()
        .atMost(20, TimeUnit.SECONDS)
        .until(
            () ->
                webClient
                    .get()
                    .uri("/users/by_name/joe")
                    .retrieve()
                    .bodyToFlux(User.class)
                    .toStream()
                    .collect(Collectors.toList())
                    .size(),
            new IsEqual(2));
  }
}
