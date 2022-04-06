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

package com.akkaserverless.javasdk.testkit.impl;

import com.akkaserverless.javasdk.action.ActionContext

import java.util.{ HashMap, Optional }
import java.nio.ByteBuffer
import com.akkaserverless.javasdk.action.ActionCreationContext
import akka.stream.Materializer
import com.akkaserverless.javasdk.impl.InternalContext
import com.akkaserverless.javasdk.Metadata
import scala.collection.convert.ImplicitConversions._

/**
 * INTERNAL API Used by the generated testkit
 */
final class TestKitActionContext(metadata: Metadata)
    extends AbstractTestKitContext
    with ActionContext
    with ActionCreationContext
    with InternalContext {

  def this() {
    this(Metadata.EMPTY)
  }

  override def metadata() = metadata

  override def eventSubject() = metadata.get("ce-subject")

  override def getGrpcClient[T](clientClass: Class[T], service: String): T =
    throw new UnsupportedOperationException("Testing logic using a gRPC client is not possible with the testkit")

}
