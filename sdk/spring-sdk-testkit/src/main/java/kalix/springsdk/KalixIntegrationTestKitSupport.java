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

package kalix.springsdk;

import kalix.javasdk.testkit.KalixTestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * This class provided the necessary infrastructure to run Kalix integration test for projects built
 * with the Spring SDK. Users should let their tests classes extends this class.
 *
 * <p>Requires Docker for starting a local instance of the Kalix proxy.
 *
 * <p>This class wires-up a local Kalix server using the user's defined Kalix components.
 *
 * <p>Users can interact their components using the {@link
 * org.springframework.web.reactive.function.client.WebClient} that is made available by the test
 * {@link org.springframework.context.ApplicationContext}.
 *
 * <p>On test teardown, the Kalix server and the Kalix proxy (docker container) will be stopped.
 */
@Import(KalixConfigurationTest.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class KalixIntegrationTestKitSupport {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired private KalixTestKit kalixTestKit;

  @AfterAll
  public void afterAll() {
    logger.info("Stopping Kalix TestKit...");
    kalixTestKit.stop();
  }
}
