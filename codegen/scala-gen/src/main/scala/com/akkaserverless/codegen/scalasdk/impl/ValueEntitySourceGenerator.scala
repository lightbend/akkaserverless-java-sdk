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
import com.lightbend.akkasls.codegen.Format

object ValueEntitySourceGenerator {
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._

  def generateUnmanaged(valueEntity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): Seq[File] =
    Seq(generateImplementationSkeleton(valueEntity, service))

  def generateManaged(valueEntity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): Seq[File] =
    Seq(abstractEntity(valueEntity, service), handler(valueEntity, service), provider(valueEntity, service))

  private[codegen] def abstractEntity(
      valueEntity: ModelBuilder.ValueEntity,
      service: ModelBuilder.EntityService): File = {
    val stateType = valueEntity.state.fqn.name
    val abstractEntityName = valueEntity.abstractEntityName

    implicit val imports =
      generateImports(
        Seq(valueEntity.state.fqn) ++
        service.commands.map(_.inputType) ++
        service.commands.map(_.outputType),
        valueEntity.fqn.parent.scalaPackage,
        otherImports = Seq("com.akkaserverless.scalasdk.valueentity.ValueEntity"),
        packageImports = Seq(service.fqn.parent.scalaPackage),
        semi = false)

    val methods = service.commands
      .map { cmd =>
        val methodName = cmd.name

        val inputType = typeName(cmd.inputType)
        val outputType = typeName(cmd.outputType)

        s"""|/** Command handler for "${cmd.name}". */
            |def ${lowerFirst(methodName)}(currentState: $stateType, ${lowerFirst(cmd.inputType.name)}: $inputType): ValueEntity.Effect[$outputType]
            |""".stripMargin

      }

    File(
      valueEntity.fqn.parent.scalaPackage,
      abstractEntityName,
      s"""|package ${valueEntity.fqn.parent.scalaPackage}
        |
        |$imports
        |
        |/** A value entity. */
        |abstract class $abstractEntityName extends ValueEntity[$stateType] {
        |
        |  ${Format.indent(methods, 2)}
        |}
        |""".stripMargin)
  }

  private[codegen] def handler(valueEntity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): File = {
    val stateType = valueEntity.state.fqn.name
    val packageName = valueEntity.fqn.parent.scalaPackage
    val valueEntityName = valueEntity.fqn.name
    implicit val imports =
      generateImports(
        Seq(valueEntity.state.fqn) ++
        service.commands.map(_.inputType) ++
        service.commands.map(_.outputType),
        valueEntity.fqn.parent.scalaPackage,
        otherImports = Seq(
          "com.akkaserverless.scalasdk.valueentity.CommandContext",
          "com.akkaserverless.scalasdk.valueentity.ValueEntity",
          "com.akkaserverless.scalasdk.impl.valueentity.ValueEntityHandler",
          "com.akkaserverless.scalasdk.impl.valueentity.ValueEntityHandler.CommandHandlerNotFound"),
        packageImports = Seq(service.fqn.parent.scalaPackage),
        semi = false)

    val commandCases = service.commands
      .map { cmd =>
        val methodName = cmd.name
        val inputType = typeName(cmd.inputType)
        s"""|case "$methodName" =>
            |  entity.${lowerFirst(methodName)}(state, command.asInstanceOf[$inputType])
            |""".stripMargin
      }

    File(
      packageName,
      valueEntityName + "Handler",
      s"""|package $packageName
        |
        |$imports
        |
        |/**
        | * A value entity handler that is the glue between the Protobuf service <code>CounterService</code>
        | * and the command handler methods in the <code>Counter</code> class.
        | */
        |class ${valueEntityName}Handler(entity: ${valueEntityName}) extends ValueEntityHandler[$stateType, ${valueEntityName}](entity) {
        |  def handleCommand(commandName: String, state: $stateType, command: Any, context: CommandContext): ValueEntity.Effect[_] = {
        |    commandName match {
        |      ${Format.indent(commandCases, 6)}
        |
        |      case _ =>
        |        throw new ValueEntityHandler.CommandHandlerNotFound(commandName)
        |    }
        |  }
        |}
        |""".stripMargin)
  }

  def provider(entity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): File = {
    val packageName = entity.fqn.parent.scalaPackage
    val className = entity.fqn.name + "Provider"

    implicit val imports: Imports = generateImports(
      Seq(entity.state.fqn) ++ service.commands.map(_.inputType) ++ service.commands.map(_.outputType),
      packageName,
      Seq(
        "com.akkaserverless.scalasdk.valueentity.ValueEntityContext",
        "com.akkaserverless.scalasdk.valueentity.ValueEntityOptions",
        "com.akkaserverless.scalasdk.valueentity.ValueEntityProvider",
        "com.google.protobuf.Descriptors"),
      packageImports = Seq(service.fqn.parent.scalaPackage),
      semi = false)

    File(
      s"${packageAsPath(packageName)}/${className}.scala",
      s"""
         |package $packageName
         |
         |$imports
         |
         |object $className {
         |  def apply(entityFactory: ValueEntityContext => ${entity.fqn.name}): $className =
         |    new $className(entityFactory, ValueEntityOptions.defaults)
         |}
         |class $className private(entityFactory: ValueEntityContext => ${entity.fqn.name}, override val options: ValueEntityOptions)
         |  extends ValueEntityProvider[${typeName(entity.state.fqn)}, ${typeName(entity.fqn)}] {
         |
         |  def withOptions(newOptions: ValueEntityOptions): $className =
         |    new $className(entityFactory, newOptions)
         |
         |  override final val serviceDescriptor: Descriptors.ServiceDescriptor =
         |    ${typeName(service.descriptorObject)}.javaDescriptor.findServiceByName("${service.fqn.protoName}")
         |
         |  override final val entityType = "${entity.entityType}"
         |
         |  override final def newHandler(context: ValueEntityContext): ${entity.handlerName} =
         |    new ${entity.handlerName}(entityFactory(context))
         |
         |  override final val additionalDescriptors =
         |    ${typeName(service.descriptorObject)}.javaDescriptor :: Nil
         |}
         |""".stripMargin)
  }

  def generateImplementationSkeleton(
      valueEntity: ModelBuilder.ValueEntity,
      service: ModelBuilder.EntityService): File = {
    implicit val imports: Imports =
      generateImports(
        Seq(valueEntity.state.fqn) ++
        service.commands.map(_.inputType) ++
        service.commands.map(_.outputType),
        valueEntity.fqn.parent.scalaPackage,
        otherImports = Seq(
          "com.akkaserverless.scalasdk.valueentity.ValueEntity",
          "com.akkaserverless.scalasdk.valueentity.ValueEntityContext"),
        packageImports = Seq(service.fqn.parent.scalaPackage),
        semi = false)

    val methods = service.commands.map { cmd =>
      s"""|override def ${lowerFirst(cmd.name)}(currentState: ${typeName(valueEntity.state.fqn)}, command: ${typeName(
        cmd.inputType)}): ValueEntity.Effect[${typeName(cmd.outputType)}] =
          |  effects.error("The command handler for `${cmd.name}` is not implemented, yet")
          |""".stripMargin
    }

    File(
      valueEntity.fqn.fileBasename + ".scala",
      s"""
         |package ${valueEntity.fqn.parent.scalaPackage}
         |
         |$imports
         |
         |/** A value entity. */
         |class ${valueEntity.fqn.name}(context: ValueEntityContext) extends ${valueEntity.abstractEntityName} {
         |  override def emptyState: ${typeName(valueEntity.state.fqn)} =
         |    throw new UnsupportedOperationException("Not implemented yet, replace with your empty entity state")
         |
         |  ${Format.indent(methods, 2)}
         |}
         |""".stripMargin)
  }
}
