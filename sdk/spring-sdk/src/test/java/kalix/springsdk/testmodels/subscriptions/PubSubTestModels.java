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

package kalix.springsdk.testmodels.subscriptions;

import kalix.javasdk.action.Action;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Publish;
import kalix.springsdk.testmodels.Message;
import kalix.springsdk.testmodels.Message2;
import kalix.springsdk.testmodels.valueentity.Counter;
import kalix.springsdk.testmodels.valueentity.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

public class PubSubTestModels {

  public static class SubscribeToValueEntityAction extends Action {

    @Subscribe.ValueEntity(Counter.class)
    public Action.Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }

    @Subscribe.ValueEntity(Counter.class)
    public Action.Effect<Message> messageTwo(Message message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToTopicAction extends Action {

    @Subscribe.Topic(value = "topicXYZ", consumerGroup = "cg")
    public Action.Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToTwoTopicsAction extends Action {

    @Subscribe.Topic("topicXYZ")
    public Action.Effect<Message> methodOne(Message message) {
      return effects().reply(message);
    }

    @Subscribe.Topic("topicXYZ")
    public Action.Effect<Message2> methodTwo(Message2 message) {
      return effects().reply(message);
    }
  }

  public static class PublishToTopicAction extends Action {

    @Publish.Topic("topicAlpha")
    public Action.Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  public static class RestWithPublishToTopicAction extends Action {

    @PostMapping("/message/{msg}")
    @Publish.Topic("foobar")
    public Effect<Message> messageOne(@PathVariable String msg) {
      return effects().reply(new Message(msg));
    }
  }

  public static class RestAnnotatedSubscribeToValueEntityAction extends Action {
    // this should fail as not allowed
    @Subscribe.ValueEntity(Counter.class)
    @PostMapping("/message/one")
    public Action.Effect<Message> messageOne(@RequestBody Message message) {
      return effects().reply(message);
    }
  }
}
