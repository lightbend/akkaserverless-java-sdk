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

package com.example.wiring.actions.echo;

import kalix.javasdk.Metadata;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.ByteBuffer;
import java.util.List;

public class ActionWithMetadata extends Action {

  private KalixClient kalixClient;

  public ActionWithMetadata(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @GetMapping("/action-with-meta/{key}/{value}")
  public Effect<Message> actionWithMeta(@PathVariable String key, @PathVariable String value) {
    var def = kalixClient.get("/return-meta/" + key, Message.class);
    return effects().forward(def.withMetadata(Metadata.EMPTY.add(key, value)));
  }


  @GetMapping("/return-meta/{key}")
  public Effect<Message> returnMeta(@PathVariable String key) {
    var metaValue = actionContext().metadata().get(key).get();
    return effects().reply(new Message(metaValue));
  }

  private Metadata.MetadataEntry stringValue(String key, String value) {
    return new Metadata.MetadataEntry() {
      @Override
      public String getKey() {
        return key;
      }

      @Override
      public String getValue() {
        return value;
      }

      @Override
      public ByteBuffer getBinaryValue() {
        return null;
      }

      @Override
      public boolean isText() {
        return true;
      }

      @Override
      public boolean isBinary() {
        return false;
      }
    };
  }
}
