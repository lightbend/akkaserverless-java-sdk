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

package com.akkaserverless.javasdk.impl
import com.google.protobuf.Descriptors

/**
 * Service describes a component type in a way which makes it possible to deploy.
 */
trait Service {

  /**
   * @return
   *   a Protobuf ServiceDescriptor of its externally accessible gRPC API
   */
  def descriptor: Descriptors.ServiceDescriptor

  /**
   * @return
   *   the type of component represented by this service
   */
  def componentType: String

  /**
   * @return
   *   the entity type name used for the entities represented by this service
   */
  def entityType: String = descriptor.getName

  /**
   * @return
   *   the options [[ComponentOptions]] or [[EntityOptions]] used by this service
   */
  def componentOptions: Option[ComponentOptions] = None

  /**
   * @return
   *   a dictionary of service methods (Protobuf Descriptors.MethodDescriptor) classified by method name. The dictionary
   *   values represent a mapping of Protobuf Descriptors.MethodDescriptor with its input and output types (see
   *   [[com.akkaserverless.javasdk.impl.ResolvedServiceMethod]])
   */
  def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]]
}
