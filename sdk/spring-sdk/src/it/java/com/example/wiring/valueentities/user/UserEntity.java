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

package com.example.wiring.valueentities.user;

import io.grpc.Status;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.valueentity.ValueEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import org.springframework.web.bind.annotation.*;

@EntityKey("id")
@EntityType("user")
@RequestMapping("/user/{id}")
public class UserEntity extends ValueEntity<User> {

  private final ValueEntityContext context;

  public UserEntity(ValueEntityContext context) {
    this.context = context;
  }

  @GetMapping
  public Effect<User> getUser() {
    if (currentState() == null)
      return effects().error("User not found", Status.Code.NOT_FOUND);

    return effects().reply(currentState());
  }

  @PostMapping("/{email}/{name}")
  public Effect<String> createOrUpdateUser(@PathVariable String email, @PathVariable String name) {
    return effects().updateState(new User(email, name)).thenReply("Ok");
  }

  @PutMapping("/{email}/{name}")
  public Effect<String> createUser(@PathVariable String email, @PathVariable String name) {
    return effects().updateState(new User(email, name)).thenReply("Ok from put");
  }

  @PatchMapping("/email/{email}")
  public Effect<String> createUser(@PathVariable String email) {
    return effects().updateState(new User(email, currentState().name)).thenReply("Ok from patch");
  }

  @DeleteMapping
  public Effect<String> deleteUser() {
    return effects().deleteState().thenReply("Ok from delete");
  }
}
