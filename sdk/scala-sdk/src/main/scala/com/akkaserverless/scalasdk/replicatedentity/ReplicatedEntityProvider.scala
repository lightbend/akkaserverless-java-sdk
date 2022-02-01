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

package com.akkaserverless.scalasdk.replicatedentity

import scala.collection.immutable.Seq

import com.akkaserverless.javasdk.impl.Serializer
import com.akkaserverless.replicatedentity.ReplicatedData
import com.akkaserverless.scalasdk.impl.replicatedentity.ReplicatedEntityRouter
import com.google.protobuf.Descriptors

trait ReplicatedEntityProvider[D <: ReplicatedData, E <: ReplicatedEntity[D]] {

  def entityType: String
  def options: ReplicatedEntityOptions
  def newRouter(context: ReplicatedEntityContext): ReplicatedEntityRouter[D, E]

  def serviceDescriptor: Descriptors.ServiceDescriptor
  def additionalDescriptors: Seq[Descriptors.FileDescriptor]
}
