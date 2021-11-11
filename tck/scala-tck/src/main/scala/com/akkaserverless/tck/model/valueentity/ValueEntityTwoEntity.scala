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

package com.akkaserverless.tck.model.valueentity

import com.akkaserverless.scalasdk.valueentity.ValueEntity
import com.akkaserverless.scalasdk.valueentity.ValueEntityContext

/** A value entity. */
class ValueEntityTwoEntity(context: ValueEntityContext) extends AbstractValueEntityTwoEntity {
  override def emptyState: Persisted = Persisted.defaultInstance

  override def call(currentState: Persisted, request: Request): ValueEntity.Effect[Response] =
    effects.reply(Response.defaultInstance)
}
