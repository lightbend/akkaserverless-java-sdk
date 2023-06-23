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

package kalix.javasdk.impl.reflection

import java.lang.reflect.AnnotatedElement

import kalix.javasdk.annotations.EntityKey
import kalix.javasdk.annotations.GenerateEntityKey
import java.lang.reflect.Method

import kalix.javasdk.annotations.GenerateId
import kalix.javasdk.annotations.Id

object EntityKeyExtractor {

  def shouldGenerateId(annotatedElement: AnnotatedElement) =
    if (annotatedElement.getAnnotation(classOf[GenerateId]) != null)
      true
    else
      annotatedElement.getAnnotation(classOf[GenerateEntityKey]) != null

  def extractEntityKeys(component: Class[_], method: Method): Seq[String] = {

    def idValue(annotatedElement: AnnotatedElement) =
      if (annotatedElement.getAnnotation(classOf[Id]) != null)
        annotatedElement.getAnnotation(classOf[Id]).value()
      else if (annotatedElement.getAnnotation(classOf[EntityKey]) != null)
        annotatedElement.getAnnotation(classOf[EntityKey]).value()
      else
        Array.empty[String]

    val entityKeysOnType = idValue(component)
    val entityKeyOnMethod = idValue(method)

    if (entityKeyOnMethod.nonEmpty && shouldGenerateId(method))
      throw ServiceIntrospectionException(
        method,
        "Invalid annotation usage. Found both @Id and @GenerateId annotations. " +
        "A method can only be annotated with one of them, but not both.")

    // keys defined on Method level get precedence
    val entityKeysToUse =
      if (entityKeyOnMethod.nonEmpty) entityKeyOnMethod
      else entityKeysOnType

    if (entityKeysToUse.isEmpty && !shouldGenerateId(method))
      throw ServiceIntrospectionException(
        method,
        "Invalid command method. No @Id nor @GenerateId annotations found. " +
        "A command method should be annotated with either @Id or @GenerateId, or " +
        "an @Id annotation should be present at class level.")

    entityKeysToUse.toIndexedSeq
  }
}
