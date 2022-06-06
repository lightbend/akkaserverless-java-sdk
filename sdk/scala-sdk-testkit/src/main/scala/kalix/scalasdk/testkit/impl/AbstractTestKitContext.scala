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

package kalix.scalasdk.testkit.impl

import akka.stream.Materializer
import kalix.scalasdk.Context
import kalix.scalasdk.impl.InternalContext
import kalix.scalasdk.testkit.MockRegistry

class AbstractTestKitContext(mockRegistry: MockRegistry = MockRegistry.empty) extends Context with InternalContext {
  override def materializer(): Materializer =
    throw new UnsupportedOperationException("Accessing the materializer from testkit not supported yet")
  def getComponentGrpcClient[T](serviceClass: Class[T]): T = {
    mockRegistry
      .get(serviceClass)
      .getOrElse(throw new UnsupportedOperationException(
        s"Could not find mock for component of type $serviceClass. Hint: use ${classOf[MockRegistry].getName} to provide an instance when testing services calling other components."))
  }
}
