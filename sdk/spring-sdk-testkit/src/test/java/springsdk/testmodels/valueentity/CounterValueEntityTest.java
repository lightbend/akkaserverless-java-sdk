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

package springsdk.testmodels.valueentity;

import kalix.javasdk.testkit.ValueEntityResult;
import kalix.springsdk.testkit.ValueEntityTestKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterValueEntityTest {

  @Test
  public void testIncrease() {
    ValueEntityTestKit<Integer, CounterValueEntity> testKit =
        ValueEntityTestKit.of(ctx -> new CounterValueEntity());
    ValueEntityResult<String> result = testKit.call(entity -> entity.increaseBy(10));
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Ok");
    assertEquals(testKit.getState(), 10);
  }

  @Test
  public void testIncreaseWithNegativeValue() {
    ValueEntityTestKit<Integer, CounterValueEntity> testKit =
        ValueEntityTestKit.of(ctx -> new CounterValueEntity());
    ValueEntityResult<String> result = testKit.call(entity -> entity.increaseBy(-10));
    assertTrue(result.isError());
    assertEquals(result.getError(), "Can't increase with a negative value");
  }
}
