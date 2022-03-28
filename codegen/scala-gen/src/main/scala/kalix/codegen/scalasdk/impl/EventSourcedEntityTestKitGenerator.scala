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

object EventSourcedEntityTestKitGenerator {
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._
  import ScalaGeneratorUtils._

  def generateUnmanagedTest(
      main: ProtoMessageType,
      entity: ModelBuilder.EventSourcedEntity,
      service: ModelBuilder.EntityService): Seq[File] =
    Seq(test(entity, service), integrationTest(main, service))

  def generateManagedTest(
      eventSourcedEntity: ModelBuilder.EventSourcedEntity,
      service: ModelBuilder.EntityService): Seq[File] =
    Seq(testKit(eventSourcedEntity, service))

  def testKit(entity: ModelBuilder.EventSourcedEntity, service: ModelBuilder.EntityService): File = {
    val className = s"${entity.messageType.name}TestKit"

    implicit val imports =
      generateImports(
        Seq(entity.state.messageType) ++
        service.commands.map(_.inputType) ++
        service.commands.map(_.outputType),
        entity.messageType.parent.scalaPackage,
        Seq(
          "com.akkaserverless.scalasdk.testkit.EventSourcedResult",
          "com.akkaserverless.scalasdk.testkit.impl.EventSourcedResultImpl",
          "com.akkaserverless.scalasdk.eventsourcedentity.EventSourcedEntity",
          "com.akkaserverless.scalasdk.eventsourcedentity.EventSourcedEntityContext",
          "com.akkaserverless.scalasdk.testkit.impl.TestKitEventSourcedEntityContext",
          "com.akkaserverless.scalasdk.testkit.impl.EventSourcedEntityEffectsRunner",
          "com.akkaserverless.scalasdk.testkit.impl.TestKitEventSourcedEntityCommandContext",
          "com.akkaserverless.scalasdk.testkit.impl.TestKitEventSourcedEntityEventContext",
          "scala.collection.immutable.Seq"),
        packageImports = Seq(service.messageType.parent.scalaPackage))

    val eventHandlers = entity.events.map { event =>
      s"""|case e: ${typeName(event.messageType)} =>
          |  entity.${lowerFirst(event.messageType.name)}(state, e)
         |""".stripMargin
    }

    val methods = service.commands.map { cmd =>
      s"""|def ${lowerFirst(cmd.name)}(command: ${typeName(cmd.inputType)}): EventSourcedResult[${typeName(
        cmd.outputType)}] =
          |  interpretEffects(() => entity.${lowerFirst(cmd.name)}(currentState, command))
         |""".stripMargin
    }

    File(
      entity.messageType.fileBasename + "TestKit.scala",
      s"""package ${entity.messageType.parent.scalaPackage}
       |
       |${writeImports(imports)}
       |
       |$managedComment
       |
       |/**
       | * TestKit for unit testing ${entity.messageType.name}
       | */
       |object $className {
       |  /**
       |   * Create a testkit instance of ${entity.messageType.name}
       |   * @param entityFactory A function that creates a ${entity.messageType.name} based on the given EventSourcedEntityContext,
       |   *                      a default entity id is used.
       |   */
       |  def apply(entityFactory: EventSourcedEntityContext => ${typeName(entity.messageType)}): $className =
       |    apply("testkit-entity-id", entityFactory)
       |  /**
       |   * Create a testkit instance of ${entity.messageType.name} with a specific entity id.
       |   */
       |  def apply(entityId: String, entityFactory: EventSourcedEntityContext => ${entity.messageType.name}): ${{
        entity.messageType.name
      }}TestKit =
       |    new ${entity.messageType.name}TestKit(entityFactory(new TestKitEventSourcedEntityContext(entityId)))
       |}
       |final class $className private(entity: ${typeName(
        entity.messageType)}) extends EventSourcedEntityEffectsRunner[${typeName(
        entity.state.messageType)}](entity: ${typeName(entity.messageType)}) {
       |
       |  override protected def handleEvent(state: ${typeName(entity.state.messageType)}, event: Any): ${typeName(
        entity.state.messageType)} = {
       |    event match {
       |      ${Format.indent(eventHandlers, 6)}
       |    }
       |  }
       |
       |  ${Format.indent(methods, 2)}
       |}
       |""".stripMargin)
  }

  def test(entity: ModelBuilder.EventSourcedEntity, service: ModelBuilder.EntityService): File = {
    val className = s"${entity.messageType.name}Spec"
    implicit val imports = generateImports(
      Seq(entity.state.messageType) ++
      service.commands.map(_.inputType) ++
      service.commands.map(_.outputType),
      entity.messageType.parent.scalaPackage,
      Seq(
        "com.akkaserverless.scalasdk.eventsourcedentity.EventSourcedEntity",
        "com.akkaserverless.scalasdk.testkit.EventSourcedResult",
        "org.scalatest.matchers.should.Matchers",
        "org.scalatest.wordspec.AnyWordSpec"),
      packageImports = Seq(service.messageType.parent.scalaPackage))

    val testKitClassName = s"${entity.messageType.name}TestKit"

    val dummyTestCases = service.commands.map { command =>
      s"""|"correctly process commands of type ${command.name}" in {
          |  val testKit = $testKitClassName(new ${entity.messageType.name}(_))
          |  // val result: EventSourcedResult[${typeName(command.outputType)}] = testKit.${lowerFirst(
        command.name)}(${typeName(command.inputType)}(...))
          |}
         |""".stripMargin
    }

    File(
      entity.messageType.fileBasename + "Spec.scala",
      s"""package ${entity.messageType.parent.scalaPackage}
         |
         |${writeImports(imports)}
         |
         |$unmanagedComment
         |
         |class $className extends AnyWordSpec with Matchers {
         |  "The ${entity.messageType.name}" should {
         |    "have example test that can be removed" in {
         |      val testKit = $testKitClassName(new ${entity.messageType.name}(_))
         |      // use the testkit to execute a command:
         |      // val result: EventSourcedResult[R] = testKit.someOperation(SomeRequest("id"));
         |      // verify the emitted events
         |      // val actualEvent: ExpectedEvent = result.nextEventOfType[ExpectedEvent]
         |      // actualEvent shouldBe expectedEvent
         |      // verify the final state after applying the events
         |      // testKit.state() shouldBe expectedState
         |      // verify the response
         |      // result.reply shouldBe expectedReply
         |      // verify the final state after the command
         |    }
         |
         |    ${Format.indent(dummyTestCases, 4)}
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
