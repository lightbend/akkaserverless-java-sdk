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

package com.lightbend.akkasls.codegen.java

import com.lightbend.akkasls.codegen.File
import com.lightbend.akkasls.codegen.Format
import com.lightbend.akkasls.codegen.ProtoMessageType
import com.lightbend.akkasls.codegen.GeneratedFiles
import com.lightbend.akkasls.codegen.ModelBuilder
import com.lightbend.akkasls.codegen.ModelBuilder.ActionService
import com.lightbend.akkasls.codegen.ModelBuilder.EntityService
import com.lightbend.akkasls.codegen.ModelBuilder.Service
import com.lightbend.akkasls.codegen.ModelBuilder.ViewService
import com.lightbend.akkasls.codegen.PackageNaming

/**
 * Generates convenience accessors for other components in the same service, accessible from actions
 */
object ComponentsSourceGenerator {
  import JavaGeneratorUtils._
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._

  private final case class CallableComponent(
      uniqueName: String,
      service: ModelBuilder.Service,
      callableCommands: Iterable[ModelBuilder.Command])

  def generate(pkg: PackageNaming, serviceMap: Map[String, Service]): GeneratedFiles = {

    // since we want to flatten component names to as short as possible there may be duplicate
    // names, so for those we need to use a longer name
    val services = serviceMap.values.toSeq
    val uniqueNamesAndComponents = services
      .flatMap { component =>
        val callableCommands = callableCommandsFor(component)
        if (callableCommands.nonEmpty) {
          val name = nameFor(component)
          if (services.exists(other => other != component && nameFor(other) == name)) {
            // conflict give it a longer unique name
            // FIXME component.messageType.name is the gRPC service name, not the component class which is what we want
            val uniqueName =
              component.messageType.parent.javaPackage.replaceAllLiterally(".", "_") + "_" + component.messageType.name
            Some(CallableComponent(uniqueName, component, callableCommands))
          } else {
            Some(CallableComponent(name, component, callableCommands))
          }
        } else {
          None
        }
      }

    GeneratedFiles.Empty
      .addManaged(File.java(pkg, "Components", generateComponentsInterface(pkg.javaPackage, uniqueNamesAndComponents)))
      .addManaged(File.java(pkg, "ComponentsImpl", generateComponentsImpl(pkg.javaPackage, uniqueNamesAndComponents)))
  }

  private def generateComponentsInterface(packageName: String, callableComponents: Seq[CallableComponent]): String = {
    val imports = generateImports(Nil, packageName, otherImports = Seq("com.akkaserverless.javasdk.DeferredCall"))

    val componentCalls = callableComponents.map { component =>
      // higher risk of name conflicts here where all components in the service meets up, so all
      // type names are fully qualified rather than imported
      val methods = component.callableCommands
        .map { command =>
          val inputType = fullyQualifiedMessage(command.inputType)
          val outputType = fullyQualifiedMessage(command.outputType)
          s"""DeferredCall<$inputType, $outputType> ${lowerFirst(command.name)}($inputType ${lowerFirst(
            command.inputType.name)});
             |""".stripMargin
        }

      s"""interface ${component.uniqueName}Calls {
         |  ${Format.indent(methods, 2)}
         |}""".stripMargin
    }

    val componentGetters = callableComponents.map { component =>
      s"${component.uniqueName}Calls ${lowerFirst(component.uniqueName)}();"
    }

    s"""package $packageName;
      |
      |${writeImports(imports)}
      |
      |$managedComment
      |
      |/**
      | * Not intended for user extension, provided through generated implementation
      | */
      |public interface Components {
      |  ${Format.indent(componentGetters, 2)}
      |
      |  ${Format.indent(componentCalls, 2)}
      |}
      |""".stripMargin
  }

  private def generateComponentsImpl(packageName: String, components: Seq[CallableComponent]): String = {
    val imports = generateImports(
      Nil,
      packageName,
      otherImports = Seq(
        "com.akkaserverless.javasdk.DeferredCall",
        "com.akkaserverless.javasdk.Context",
        "com.akkaserverless.javasdk.impl.DeferredCallImpl",
        "com.akkaserverless.javasdk.impl.MetadataImpl",
        "com.akkaserverless.javasdk.impl.InternalContext"))

    val componentGetters = components.map { component =>
      s"""@Override
         |public Components.${component.uniqueName}Calls ${lowerFirst(component.uniqueName)}() {
         |  return new ${component.uniqueName}CallsImpl();
         |}""".stripMargin
    }

    val componentCallImpls = components.map { component =>
      val methods = component.callableCommands
        .map { command =>
          val commandMethod = lowerFirst(command.name)
          val paramName = lowerFirst(command.inputType.name)
          val inputType = fullyQualifiedMessage(command.inputType)
          val outputType = fullyQualifiedMessage(command.outputType)
          s"""@Override
             |public DeferredCall<$inputType, $outputType> $commandMethod($inputType $paramName) {
             |  return new DeferredCallImpl<>(
             |    ${lowerFirst(command.inputType.name)},
             |    MetadataImpl.Empty(),
             |    "${component.service.messageType.fullyQualifiedProtoName}",
             |    "${command.name}",
             |    () -> getGrpcClient(${component.service.messageType.fullyQualifiedGrpcServiceInterfaceName}.class).$commandMethod($paramName)
             |  );
             |}""".stripMargin
        }

      s"""private final class ${component.uniqueName}CallsImpl implements Components.${component.uniqueName}Calls {
         |   ${Format.indent(methods, 2)}
         |}""".stripMargin

    }

    s"""package $packageName;
       |
       |${writeImports(imports)}
       |
       |$managedComment
       |
       |/**
       | * Not intended for direct instantiation, called by generated code, use Action.components() to access
       | */
       |public final class ComponentsImpl implements Components {
       |
       |  private final InternalContext context;
       |
       |  public ComponentsImpl(Context context) {
       |    this.context = (InternalContext) context;
       |  }
       |
       |  private <T> T getGrpcClient(Class<T> serviceClass) {
       |    return context.getComponentGrpcClient(serviceClass);
       |  }
       |
       |  ${Format.indent(componentGetters, 2)}
       |
       |  ${Format.indent(componentCallImpls, 2)}
       |}
       |""".stripMargin
  }

  private def fullyQualifiedMessage(messageType: ProtoMessageType): String =
    s"${messageType.parent.javaPackage}.${messageType.fullName}"

  private def nameFor(component: Service): String = {
    component match {
      case as: ActionService => as.className.split('.').last
      case es: EntityService => es.componentFullName.split('.').last
      case vs: ViewService   => vs.className.split('.').last
    }
  }

  private def callableCommandsFor(service: Service): Iterable[ModelBuilder.Command] =
    service match {
      case view: ViewService =>
        // only queries, not update commands for views
        view.queries.filter(_.isUnary)
      case _ =>
        // only unary commands for now
        service.commands.filter(_.isUnary)
    }
}
