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
import com.lightbend.akkasls.codegen.ModelBuilder
import com.lightbend.akkasls.codegen.PackageNaming
import com.lightbend.akkasls.codegen.ClassMessageType
import com.lightbend.akkasls.codegen.ProtoMessageType

object ValueEntitySourceGenerator {
  import ScalaGeneratorUtils._
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._

  def generateUnmanaged(valueEntity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): Seq[File] =
    Seq(generateImplementationSkeleton(valueEntity, service))

  def generateManaged(
      valueEntity: ModelBuilder.ValueEntity,
      service: ModelBuilder.EntityService,
      mainPackageName: PackageNaming): Seq[File] =
    Seq(
      abstractEntity(valueEntity, service, mainPackageName),
      handler(valueEntity, service),
      provider(valueEntity, service))

  private[codegen] def abstractEntity(
      valueEntity: ModelBuilder.ValueEntity,
      service: ModelBuilder.EntityService,
      mainPackageName: PackageNaming): File = {
    import Types.ValueEntity._

    val stateType = valueEntity.state.messageType
    val abstractEntityName = valueEntity.abstractEntityName

    val methods = service.commands
      .map { cmd =>
        val methodName = cmd.name

        val inputType = cmd.inputType
        val outputType = cmd.outputType

        c"""|def ${lowerFirst(methodName)}(currentState: $stateType, ${lowerFirst(cmd.inputType.name)}: $inputType): $ValueEntity.Effect[$outputType]
            |"""

      }

    val Components = ClassMessageType(mainPackageName.javaPackage + ".Components")
    val ComponentsImpl = ClassMessageType(mainPackageName.javaPackage + ".ComponentsImpl")

    generate(
      valueEntity.messageType.parent,
      abstractEntityName,
      c"""|$managedComment
          |
          |abstract class $abstractEntityName extends $ValueEntity[$stateType] {
          |
          |  def components: $Components =
          |    new ${ComponentsImpl}(commandContext())
          |
          |  $methods
          |}
          |""",
      packageImports = Seq(service.messageType.parent))
  }

  private[codegen] def handler(valueEntity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): File = {
    import Types.ValueEntity._
    val stateType = valueEntity.state.messageType
    val valueEntityName = valueEntity.messageType

    val commandCases = service.commands
      .map { cmd =>
        val methodName = cmd.name
        val inputType = cmd.inputType
        c"""|case "$methodName" =>
            |  entity.${lowerFirst(methodName)}(state, command.asInstanceOf[$inputType])
            |"""
      }

    generate(
      valueEntity.messageType.parent,
      valueEntity.routerName,
      c"""|$managedComment
          |
          |/**
          | * A value entity handler that is the glue between the Protobuf service <code>CounterService</code>
          | * and the command handler methods in the <code>Counter</code> class.
          | */
          |class ${valueEntityName.name}Router(entity: $valueEntityName) extends $ValueEntityRouter[$stateType, $valueEntityName](entity) {
          |  def handleCommand(commandName: String, state: $stateType, command: Any, context: $CommandContext): $ValueEntity.Effect[_] = {
          |    commandName match {
          |      $commandCases
          |      case _ =>
          |        throw new $CommandHandlerNotFound(commandName)
          |    }
          |  }
          |}
          |""",
      packageImports = Seq(service.messageType.parent))
  }

  def provider(entity: ModelBuilder.ValueEntity, service: ModelBuilder.EntityService): File = {
    import Types.ValueEntity._
    import Types.Descriptors
    import Types.ImmutableSeq
    val className = entity.providerName

    val relevantTypes = allRelevantMessageTypes(service, entity)
    val relevantProtoTypes = relevantTypes.collect { case proto: ProtoMessageType => proto }

    val relevantDescriptors = relevantProtoTypes.map(_.descriptorImport).distinct

    generate(
      entity.messageType.parent,
      className,
      c"""|$managedComment
          |
          |object $className {
          |  def apply(entityFactory: $ValueEntityContext => ${entity.messageType.name}): $className =
          |    new $className(entityFactory, $ValueEntityOptions.defaults)
          |}
          |class $className private(entityFactory: $ValueEntityContext => ${entity.messageType.name}, override val options: $ValueEntityOptions)
          |  extends $ValueEntityProvider[${entity.state.messageType}, ${entity.messageType}] {
          |
          |  def withOptions(newOptions: $ValueEntityOptions): $className =
          |    new $className(entityFactory, newOptions)
          |
          |  override final val serviceDescriptor: $Descriptors.ServiceDescriptor =
          |    ${service.messageType.descriptorImport}.javaDescriptor.findServiceByName("${service.messageType.protoName}")
          |
          |  override final val entityType = "${entity.entityType}"
          |
          |  override final def newRouter(context: $ValueEntityContext): ${entity.routerName} =
          |    new ${entity.routerName}(entityFactory(context))
          |
          |  override final val additionalDescriptors: $ImmutableSeq[$Descriptors.FileDescriptor] =
          |    ${relevantDescriptors.map(d => c"$d.javaDescriptor :: ")}Nil
          |}
          |""",
      packageImports = Seq(service.messageType.parent))
  }

  def generateImplementationSkeleton(
      valueEntity: ModelBuilder.ValueEntity,
      service: ModelBuilder.EntityService): File = {
    import Types.ValueEntity._

    val methods = service.commands.map { cmd =>
      c"""|override def ${lowerFirst(cmd.name)}(currentState: ${valueEntity.state.messageType}, ${lowerFirst(
        cmd.inputType.name)}: ${cmd.inputType}): $ValueEntity.Effect[${cmd.outputType}] =
          |  effects.error("The command handler for `${cmd.name}` is not implemented, yet")
          |"""
    }

    generate(
      valueEntity.messageType.parent,
      valueEntity.messageType.name,
      c"""|$unmanagedComment
          |
          |class ${valueEntity.messageType.name}(context: $ValueEntityContext) extends ${valueEntity.abstractEntityName} {
          |  override def emptyState: ${valueEntity.state.messageType} =
          |    throw new UnsupportedOperationException("Not implemented yet, replace with your empty entity state")
          |
          |  $methods
          |}
          |""",
      packageImports = Seq(service.messageType.parent))
  }
}
