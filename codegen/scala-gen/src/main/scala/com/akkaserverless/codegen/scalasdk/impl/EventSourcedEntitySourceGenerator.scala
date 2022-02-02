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
import com.lightbend.akkasls.codegen.PojoMessageType
import com.lightbend.akkasls.codegen.ProtoMessageType

object EventSourcedEntitySourceGenerator {
  import ScalaGeneratorUtils._
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._

  def generateUnmanaged(
      eventSourcedEntity: ModelBuilder.EventSourcedEntity,
      service: ModelBuilder.EntityService): Seq[File] =
    Seq(generateImplementationSkeleton(eventSourcedEntity, service))

  def generateManaged(
      eventSourcedEntity: ModelBuilder.EventSourcedEntity,
      service: ModelBuilder.EntityService,
      mainPackageName: PackageNaming): Seq[File] =
    Seq(
      abstractEntity(eventSourcedEntity, service, mainPackageName),
      handler(eventSourcedEntity, service),
      provider(eventSourcedEntity, service))

  private[codegen] def abstractEntity(
      eventSourcedEntity: ModelBuilder.EventSourcedEntity,
      service: ModelBuilder.EntityService,
      mainPackageName: PackageNaming): File = {
    import Types.EventSourcedEntity._
    val abstractEntityName = eventSourcedEntity.abstractEntityName

    val stateType = eventSourcedEntity.state.messageType
    val commandHandlers = service.commands
      .map { cmd =>
        val methodName = cmd.name

        c"""|def ${lowerFirst(methodName)}(currentState: $stateType, ${lowerFirst(cmd.inputType.name)}: ${cmd.inputType}): $EventSourcedEntity.Effect[${cmd.outputType}]
            |"""
      }

    val eventHandlers =
      eventSourcedEntity match {
        case ModelBuilder.EventSourcedEntity(_, _, _, events) =>
          events.map { event =>
            c"""|def ${lowerFirst(event.messageType.name)}(currentState: $stateType, ${lowerFirst(
              event.messageType.name)}: ${event.messageType}): $stateType"""
          }
      }

    val Components = PojoMessageType(mainPackageName.javaPackage + ".Components")
    val ComponentsImpl = PojoMessageType(mainPackageName.javaPackage + ".ComponentsImpl")

    generate(
      eventSourcedEntity.messageType.parent,
      abstractEntityName,
      c"""|$managedComment
          |
          |/** An event sourced entity. */
          |abstract class $abstractEntityName extends $EventSourcedEntity[$stateType] {
          |
          |  def components: $Components =
          |    new ${ComponentsImpl}(commandContext())
          |
          |  $commandHandlers
          |  $eventHandlers
          |}
          |""",
      packageImports = Seq(service.messageType.parent))
  }

  private[codegen] def handler(
      eventSourcedEntity: ModelBuilder.EventSourcedEntity,
      service: ModelBuilder.EntityService): File = {
    import Types.EventSourcedEntity._

    val stateType = eventSourcedEntity.state.messageType
    val eventSourcedEntityName = eventSourcedEntity.messageType

    val eventCases = eventSourcedEntity.events.map { evt =>
      c"""|case evt: ${evt.messageType} =>
          |  entity.${lowerFirst(evt.messageType.name)}(state, evt)
          |"""
    }

    val commandCases = service.commands
      .map { cmd =>
        val methodName = cmd.name
        c"""|case "$methodName" =>
            |  entity.${lowerFirst(methodName)}(state, command.asInstanceOf[${cmd.inputType}])
            |"""
      }
    generate(
      eventSourcedEntity.messageType.parent,
      eventSourcedEntity.routerName,
      c"""|$managedComment
          |
          |/**
          | * An event sourced entity handler that is the glue between the Protobuf service <code>CounterService</code>
          | * and the command handler methods in the <code>Counter</code> class.
          | */
          |class ${eventSourcedEntityName}Router(entity: ${eventSourcedEntityName}) extends $EventSourcedEntityRouter[$stateType, $eventSourcedEntityName](entity) {
          |  def handleCommand(commandName: String, state: $stateType, command: Any, context: $CommandContext): $EventSourcedEntity.Effect[_] = {
          |    commandName match {
          |      $commandCases
          |      case _ =>
          |        throw new $CommandHandlerNotFound(commandName)
          |    }
          |  }
          |  def handleEvent(state: $stateType, event: Any): $stateType = {
          |    event match {
          |      $eventCases
          |      case _ =>
          |        throw new $EventHandlerNotFound(event.getClass)
          |    }
          |  }
          |}
          |""",
      packageImports = Seq(service.messageType.parent))
  }

  def provider(entity: ModelBuilder.EventSourcedEntity, service: ModelBuilder.EntityService): File = {
    import Types.Descriptors
    import Types.EventSourcedEntity._
    import Types.ImmutableSeq
    val className = entity.providerName

    val relevantTypes = allRelevantMessageTypes(service, entity)
    val relevantProtoTypes = relevantTypes.collect { case proto: ProtoMessageType => proto }

    val relevantDescriptors = relevantProtoTypes.map(_.descriptorImport).distinct

    generate(
      entity.messageType.parent,
      entity.providerName,
      c"""|$managedComment
          |
          |object $className {
          |  def apply(entityFactory: $EventSourcedEntityContext => ${entity.messageType.name}): $className =
          |    new $className(entityFactory, $EventSourcedEntityOptions.defaults)
          |}
          |class $className private(entityFactory: $EventSourcedEntityContext => ${entity.messageType.name}, override val options: $EventSourcedEntityOptions)
          |  extends $EventSourcedEntityProvider[${entity.state.messageType}, ${entity.messageType}] {
          |
          |  def withOptions(newOptions: $EventSourcedEntityOptions): $className =
          |    new $className(entityFactory, newOptions)
          |
          |  override final val serviceDescriptor: $Descriptors.ServiceDescriptor =
          |    ${service.messageType.descriptorImport}.javaDescriptor.findServiceByName("${service.messageType.protoName}")
          |
          |  override final val entityType: String = "${entity.entityType}"
          |
          |  override final def newRouter(context: $EventSourcedEntityContext): ${entity.routerName} =
          |    new ${entity.routerName}(entityFactory(context))
          |
          |  override final val additionalDescriptors: $ImmutableSeq[$Descriptors.FileDescriptor] =
          |    ${relevantDescriptors.distinct.map(d => c"$d.javaDescriptor ::").toVector.distinct} Nil
          |}
          |""",
      packageImports = Seq(service.messageType.parent))
  }

  def generateImplementationSkeleton(
      eventSourcedEntity: ModelBuilder.EventSourcedEntity,
      service: ModelBuilder.EntityService): File = {
    import Types.EventSourcedEntity._

    val eventHandlers =
      eventSourcedEntity.events.map { event =>
        c"""|override def ${lowerFirst(
          event.messageType.name)}(currentState: ${eventSourcedEntity.state.messageType}, ${lowerFirst(
          event.messageType.name)}: ${event.messageType}): ${eventSourcedEntity.state.messageType} =
            |  throw new RuntimeException("The event handler for `${event.messageType.name}` is not implemented, yet")
            |"""
      }

    val commandHandlers =
      service.commands.map { cmd =>
        c"""|override def ${lowerFirst(cmd.name)}(currentState: ${eventSourcedEntity.state.messageType}, ${lowerFirst(
          cmd.inputType.name)}: ${cmd.inputType}): $EventSourcedEntity.Effect[${cmd.outputType}] =
            |  effects.error("The command handler for `${cmd.name}` is not implemented, yet")
            |"""
      }

    generate(
      eventSourcedEntity.messageType.parent,
      eventSourcedEntity.messageType.name,
      c"""|$unmanagedComment
          |
          |/** An event sourced entity. */
          |class ${eventSourcedEntity.messageType.name}(context: $EventSourcedEntityContext) extends ${eventSourcedEntity.abstractEntityName} {
          |  override def emptyState: ${eventSourcedEntity.state.messageType} =
          |    throw new UnsupportedOperationException("Not implemented yet, replace with your empty entity state")
          |
          |  $commandHandlers
          |  $eventHandlers
          |}""",
      packageImports = Seq(service.messageType.parent))
  }
}
