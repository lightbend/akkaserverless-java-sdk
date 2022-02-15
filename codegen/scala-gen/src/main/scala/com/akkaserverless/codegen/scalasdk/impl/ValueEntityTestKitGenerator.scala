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

import com.lightbend.akkasls.codegen.File
import com.lightbend.akkasls.codegen.Format
import com.lightbend.akkasls.codegen.ProtoMessageType
import com.lightbend.akkasls.codegen.Imports
import com.lightbend.akkasls.codegen.ModelBuilder

object ValueEntityTestKitGenerator {
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._
  import ScalaGeneratorUtils._

  def generateUnmanagedTest(
      main: ProtoMessageType,
      valueEntity: ModelBuilder.ValueEntity,
      service: ModelBuilder.EntityService): Seq[File] =
    Seq(test(valueEntity, service), integrationTest(main, service))

  def generateManagedTest(valueEntity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): Seq[File] =
    Seq(testkit(valueEntity, service))

  private[codegen] def testkit(valueEntity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): File = {
    implicit val imports: Imports =
      generateImports(
        Seq(valueEntity.state.messageType) ++
        service.commands.map(_.inputType) ++
        service.commands.map(_.outputType),
        valueEntity.messageType.parent.scalaPackage,
        otherImports = Seq(
          "com.akkaserverless.scalasdk.testkit.ValueEntityResult",
          "com.akkaserverless.scalasdk.testkit.impl.ValueEntityResultImpl",
          "com.akkaserverless.scalasdk.valueentity.ValueEntity",
          "com.akkaserverless.scalasdk.valueentity.ValueEntityContext",
          "com.akkaserverless.scalasdk.testkit.impl.TestKitValueEntityContext"),
        packageImports = Seq(service.messageType.parent.scalaPackage))

    val entityClassName = valueEntity.messageType.name

    val methods = service.commands.map { cmd =>
      s"""|def ${lowerFirst(cmd.name)}(command: ${typeName(cmd.inputType)}): ValueEntityResult[${typeName(
        cmd.outputType)}] = {
          |  val effect = entity.${lowerFirst(cmd.name)}(state, command)
          |  interpretEffects(effect)
          |}
          |""".stripMargin
    }

    File(
      valueEntity.messageType.fileBasename + "TestKit.scala",
      s"""|package ${valueEntity.messageType.parent.scalaPackage}
          |
          |${writeImports(imports)}
          |
          |$managedComment
          |
          |/**
          | * TestKit for unit testing $entityClassName
          | */
          |object ${entityClassName}TestKit {
          |  /**
          |   * Create a testkit instance of $entityClassName
          |   * @param entityFactory A function that creates a $entityClassName based on the given ValueEntityContext,
          |   *                      a default entity id is used.
          |   */
          |  def apply(entityFactory: ValueEntityContext => $entityClassName): ${entityClassName}TestKit =
          |    apply("testkit-entity-id", entityFactory)
          |
          |  /**
          |   * Create a testkit instance of $entityClassName with a specific entity id.
          |   */
          |  def apply(entityId: String, entityFactory: ValueEntityContext => $entityClassName): ${entityClassName}TestKit =
          |    new ${entityClassName}TestKit(entityFactory(new TestKitValueEntityContext(entityId)))
          |}
          |
          |/**
          | * TestKit for unit testing $entityClassName
          | */
          |final class ${entityClassName}TestKit private(entity: $entityClassName) {
          |  private var state: ${typeName(valueEntity.state.messageType)} = entity.emptyState
          |
          |  /**
          |   * @return The current state of the $entityClassName under test
          |   */
          |  def currentState(): ${typeName(valueEntity.state.messageType)} =
          |    state
          |
          |  private def interpretEffects[Reply](effect: ValueEntity.Effect[Reply]): ValueEntityResult[Reply] = {
          |    val result = new ValueEntityResultImpl[Reply](effect)
          |    if (result.stateWasUpdated)
          |      this.state = result.updatedState.asInstanceOf[${typeName(valueEntity.state.messageType)}]
          |    result
          |  }
          |
          |  ${Format.indent(methods, 2)}
          |}
          |""".stripMargin)
  }

  def test(valueEntity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): File = {

    implicit val imports: Imports =
      generateImports(
        Seq(valueEntity.state.messageType) ++
        service.commands.map(_.inputType) ++
        service.commands.map(_.outputType),
        valueEntity.messageType.parent.scalaPackage,
        otherImports = Seq(
          "com.akkaserverless.scalasdk.valueentity.ValueEntity",
          "com.akkaserverless.scalasdk.testkit.ValueEntityResult",
          "org.scalatest.matchers.should.Matchers",
          "org.scalatest.wordspec.AnyWordSpec"),
        packageImports = Seq(service.messageType.parent.scalaPackage))

    val entityClassName = valueEntity.messageType.name

    val testCases = service.commands.map { cmd =>
      s"""|"handle command ${cmd.name}" in {
          |  val testKit = ${entityClassName}TestKit(new $entityClassName(_))
          |  // val result = testKit.${lowerFirst(cmd.name)}(${typeName(cmd.inputType)}(...))
          |}
          |""".stripMargin
    }

    File(
      valueEntity.messageType.fileBasename + "Spec.scala",
      s"""|package ${valueEntity.messageType.parent.scalaPackage}
          |
          |${writeImports(imports)}
          |
          |class ${entityClassName}Spec
          |    extends AnyWordSpec
          |    with Matchers {
          |
          |  "${entityClassName}" must {
          |
          |    "have example test that can be removed" in {
          |      val testKit = ${entityClassName}TestKit(new $entityClassName(_))
          |      // use the testkit to execute a command
          |      // and verify final updated state:
          |      // val result = testKit.someOperation(SomeRequest)
          |      // verify the response
          |      // val actualResponse = result.getReply()
          |      // actualResponse shouldBe expectedResponse
          |      // verify the final state after the command
          |      // testKit.currentState() shouldBe expectedState
          |    }
          |
          |    ${Format.indent(testCases, 4)}
          |
          |  }
          |}
          |""".stripMargin)
  }

  def integrationTest(main: ProtoMessageType, service: ModelBuilder.EntityService): File = {

    implicit val imports: Imports =
      generateImports(
        Seq(main) ++
        service.commands.map(_.inputType) ++
        service.commands.map(_.outputType),
        service.messageType.parent.scalaPackage,
        otherImports = Seq(
          "akka.actor.ActorSystem",
          "com.akkaserverless.scalasdk.testkit.AkkaServerlessTestKit",
          "org.scalatest.matchers.should.Matchers",
          "org.scalatest.wordspec.AnyWordSpec",
          "org.scalatest.BeforeAndAfterAll",
          "org.scalatest.concurrent.ScalaFutures",
          "org.scalatest.time.Span",
          "org.scalatest.time.Seconds",
          "org.scalatest.time.Millis"),
        packageImports = Nil)

    val entityClassName = service.messageType.name

    File(
      service.messageType.fileBasename + "IntegrationSpec.scala",
      s"""|package ${service.messageType.parent.scalaPackage}
          |
          |${writeImports(imports)}
          |
          |$unmanagedComment
          |
          |class ${entityClassName}IntegrationSpec
          |    extends AnyWordSpec
          |    with Matchers
          |    with BeforeAndAfterAll
          |    with ScalaFutures {
          |
          |  implicit private val patience: PatienceConfig =
          |    PatienceConfig(Span(5, Seconds), Span(500, Millis))
          |
          |  private val testKit = AkkaServerlessTestKit(Main.createAkkaServerless()).start()
          |
          |  private val client = testKit.getGrpcClient(classOf[${typeName(service.messageType)}])
          |
          |  "${entityClassName}" must {
          |
          |    "have example test that can be removed" in {
          |      // use the gRPC client to send requests to the
          |      // proxy and verify the results
          |    }
          |
          |  }
          |
          |  override def afterAll(): Unit = {
          |    testKit.stop()
          |    super.afterAll()
          |  }
          |}
          |""".stripMargin)
  }
}
