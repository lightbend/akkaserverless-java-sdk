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

package com.akkaserverless.codegen.scalasdk

import com.lightbend.akkasls.codegen.ModelBuilder

import scala.annotation.tailrec

object SourceGenerator {
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._

  val managedComment = """/* This code is managed by Akka Serverless tooling.
                         | * It will be re-generated to reflect any changes to your protobuf definitions.
                         | * DO NOT EDIT
                         | */""".stripMargin

  val unmanagedComment = """|/* This code was generated by Akka Serverless tooling.
                            | * As long as this file exists it will not be re-generated.
                            | * You are free to make changes to this file.
                            | */""".stripMargin

  /**
   * Generate the 'managed' code for this model: code that will be regenerated regularly in the 'compile' configuratio
   */
  def generateManaged(model: ModelBuilder.Model): Seq[File] =
    Seq(File("foo/bar/AbstractBaz.scala", "package foo.bar\n\nabstract class AbstractBaz"))
      .map(_.prependComment(managedComment))

  /**
   * Generate the 'managed' code for this model: code that will be regenerated regularly in the 'compile' configuratio
   */
  def generateManagedTest(model: ModelBuilder.Model): Seq[File] =
    Seq(File("foo/bar/BazSpec.scala", "package foo.bar\n\nclass BazSpec { new Baz() }"))
      .map(_.prependComment(managedComment))

  /**
   * Generate the 'unmanaged' code for this model: code that is generated once on demand and then maintained by the
   * user.
   */
  def generateUnmanaged(model: ModelBuilder.Model): Seq[File] =
    Seq(File("foo/bar/Baz.scala", "package foo.bar\n\nclass Baz extends AbstractBaz"), generateMain(model))
      .map(_.prependComment(unmanagedComment))

  def generateMain(model: ModelBuilder.Model): File = {
    val mainPackage = mainPackageName(model.services.keys ++ model.entities.keys)
    File(
      (mainPackage :+ "Main.scala").mkString("/"),
      s"""
         |package ${mainPackage.mkString(".")}
         |
         |object Main extends App {
         |  println("Hello, world!")
         |}
         |""".stripMargin)
  }

}
