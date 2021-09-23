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

package com.akkaserverless.codegen.scalasdk.impl

import com.akkaserverless.codegen.scalasdk.File
import com.lightbend.akkasls.codegen.ModelBuilder

object SourceGenerator {
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._

  /**
   * Generate the 'managed' code for this model: code that will be regenerated regularly in the 'compile' configuratio
   */
  def generateManaged(model: ModelBuilder.Model): Seq[File] = {
    MainSourceGenerator.generateManaged(model).toSeq ++
    model.services.values
      .flatMap {
        case service: ModelBuilder.EntityService =>
          model.lookupEntity(service) match {
            case entity: ModelBuilder.ValueEntity =>
              ValueEntitySourceGenerator.generateManaged(entity, service)
            case entity: ModelBuilder.EventSourcedEntity =>
              Nil // FIXME
            case entity: ModelBuilder.ReplicatedEntity =>
              Nil // FIXME
          }
        case service: ModelBuilder.ViewService =>
          ViewServiceSourceGenerator.generateManaged(service)
        case service: ModelBuilder.ActionService =>
          ActionServiceSourceGenerator.generateManaged(service)
      }
      .map(_.prepend(managedComment))
  }

  /**
   * Generate the 'managed' code for this model: code that will be regenerated regularly in the 'compile' configuratio
   */
  def generateManagedTest(model: ModelBuilder.Model): Seq[File] = Seq.empty

  /**
   * Generate the 'unmanaged' code for this model: code that is generated once on demand and then maintained by the
   * user.
   */
  def generateUnmanaged(model: ModelBuilder.Model): Seq[File] = {
    MainSourceGenerator.generateUnmanaged(model).toSeq ++
    model.services.values
      .flatMap {
        case service: ModelBuilder.EntityService =>
          model.lookupEntity(service) match {
            case entity: ModelBuilder.ValueEntity =>
              ValueEntitySourceGenerator.generateUnmanaged(entity, service)
            case entity: ModelBuilder.EventSourcedEntity =>
              Nil // FIXME
            case entity: ModelBuilder.ReplicatedEntity =>
              Nil // FIXME
          }
        case service: ModelBuilder.ViewService =>
          ViewServiceSourceGenerator.generateUnmanaged(service)
        case service: ModelBuilder.ActionService =>
          ActionServiceSourceGenerator.generateUnmanaged(service)
      }
      .map(_.prepend(unmanagedComment))
  }

}
