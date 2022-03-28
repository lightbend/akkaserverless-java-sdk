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

package kalix.javasdk.tck;

import kalix.javasdk.AkkaServerlessRunner;
import kalix.javasdk.testkit.BuildInfo;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.BaseConsumer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.IndefiniteWaitOneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;

public final class RunTck {
  public static final String TCK_IMAGE = "gcr.io/akkaserverless-public/akkaserverless-tck";
  public static final String TCK_VERSION = BuildInfo.proxyVersion();

  public static void main(String[] args) throws Exception {
    AkkaServerlessRunner runner = JavaSdkTck.SERVICE.createRunner();
    runner.run();

    Testcontainers.exposeHostPorts(8080);

    try {
      new GenericContainer<>(DockerImageName.parse(TCK_IMAGE).withTag(TCK_VERSION))
          .withEnv("TCK_SERVICE_HOST", "host.testcontainers.internal")
          .withLogConsumer(new LogConsumer().withRemoveAnsiCodes(false))
          .withStartupCheckStrategy(new IndefiniteWaitOneShotStartupCheckStrategy())
          .start();
    } catch (Exception e) {
      // container failed, exit with failure, assumes forked run
      System.exit(1);
    }

    runner.terminate().toCompletableFuture().get(); // will exit JVM on shutdown
  }

  // implement BaseConsumer so that we can disable the removal of ANSI codes -- full colour output
  static class LogConsumer extends BaseConsumer<LogConsumer> {
    @Override
    public void accept(OutputFrame outputFrame) {
      System.out.print(outputFrame.getUtf8String());
    }
  }
}
