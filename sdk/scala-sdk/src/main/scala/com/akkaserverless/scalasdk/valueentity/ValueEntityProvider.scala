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

package com.akkaserverless.scalasdk.valueentity

import scala.collection.immutable.Seq

import com.akkaserverless.javasdk.impl.Serializer
import com.akkaserverless.scalasdk.impl.valueentity.ValueEntityRouter
import com.google.protobuf.Descriptors

/**
 * Register a value based entity in {@link com.akkaserverless.scalasdk.AkkaServerless} using a <code>
 * ValueEntityProvider</code>. The concrete <code>ValueEntityProvider</code> is generated for the specific entities
 * defined in Protobuf, for example <code>CustomerEntityProvider</code>.
 */
trait ValueEntityProvider[S, E <: ValueEntity[S]] {
  def options: ValueEntityOptions

  def serviceDescriptor: Descriptors.ServiceDescriptor

  def entityType: String

  def newRouter(context: ValueEntityContext): ValueEntityRouter[S, E]

  def additionalDescriptors: Seq[Descriptors.FileDescriptor]

  def serializer: Serializer = Serializer.noopSerializer
}
