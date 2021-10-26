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

package com.lightbend.akkasls.codegen
package java

import _root_.java.nio.file.Files
import _root_.java.nio.file.Path

import com.google.common.base.Charsets
import com.lightbend.akkasls.codegen.ModelBuilder.EventSourcedEntity
import com.lightbend.akkasls.codegen.ModelBuilder.MessageTypeArgument
import com.lightbend.akkasls.codegen.ModelBuilder.ReplicatedEntity
import com.lightbend.akkasls.codegen.ModelBuilder.ValueEntity

/**
 * Responsible for generating Java source from an entity model
 */
object EntityServiceSourceGenerator {
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._
  import JavaGeneratorUtils._

  /**
   * Generate Java source from entities where the target source and test source directories have no existing source.
   * Note that we only generate tests for entities where we are successful in generating an entity. The user may not
   * want a test otherwise.
   *
   * Also generates a main source file if it does not already exist.
   *
   * Impure.
   */
  def generate(
      entity: ModelBuilder.Entity,
      service: ModelBuilder.EntityService,
      sourceDirectory: Path,
      testSourceDirectory: Path,
      integrationTestSourceDirectory: Path,
      generatedSourceDirectory: Path,
      mainClassPackageName: String,
      mainClassName: String): Iterable[Path] = {
    val packageName = entity.fqn.parent.javaPackage
    val servicePackageName = service.fqn.parent.javaPackage
    val className = entity.fqn.name
    val packagePath = packageAsPath(packageName)
    val servicePackagePath = packageAsPath(servicePackageName)

    val implClassName = className
    val implSourcePath =
      sourceDirectory.resolve(packagePath.resolve(implClassName + ".java"))

    val interfaceClassName = entity.abstractEntityName
    val interfaceSourcePath =
      generatedSourceDirectory.resolve(packagePath.resolve(interfaceClassName + ".java"))

    interfaceSourcePath.getParent.toFile.mkdirs()
    Files.write(interfaceSourcePath, interfaceSource(service, entity, packageName, className).getBytes(Charsets.UTF_8))

    if (!implSourcePath.toFile.exists()) {
      // Now we generate the entity
      implSourcePath.getParent.toFile.mkdirs()
      Files.write(
        implSourcePath,
        source(service, entity, packageName, implClassName, interfaceClassName, entity.entityType).getBytes(
          Charsets.UTF_8))
    }

    val routerClassName = entity.routerName
    val routerSourcePath = {
      val path = generatedSourceDirectory.resolve(packagePath.resolve(routerClassName + ".java"))
      path.getParent.toFile.mkdirs()
      Files.write(path, routerSource(service, entity, packageName, className).getBytes(Charsets.UTF_8))
      path
    }

    val providerClassName = entity.providerName
    val providerSourcePath = {
      val path = generatedSourceDirectory.resolve(packagePath.resolve(providerClassName + ".java"))
      path.getParent.toFile.mkdirs()
      Files.write(path, providerSource(service, entity, packageName, className).getBytes(Charsets.UTF_8))
      path
    }

    // unit test
    val testClassName = className + "Test"
    val testSourcePath =
      testSourceDirectory.resolve(packagePath.resolve(testClassName + ".java"))
    val testSourceFiles = Nil // FIXME add new unit test generation

    // integration test
    val integrationTestClassName = className + "IntegrationTest"
    val integrationTestSourcePath =
      integrationTestSourceDirectory
        .resolve(servicePackagePath.resolve(integrationTestClassName + ".java"))
    if (!integrationTestSourcePath.toFile.exists()) {
      integrationTestSourcePath.getParent.toFile.mkdirs()
      Files.write(
        integrationTestSourcePath,
        integrationTestSource(
          mainClassPackageName,
          mainClassName,
          service,
          entity,
          servicePackageName,
          integrationTestClassName).getBytes(Charsets.UTF_8))
    }
    Seq(
      implSourcePath,
      integrationTestSourcePath,
      interfaceSourcePath,
      providerSourcePath,
      routerSourcePath) ++ testSourceFiles
  }

  private[codegen] def source(
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.Entity,
      packageName: String,
      className: String,
      interfaceClassName: String,
      entityType: String): String = {
    entity match {
      case eventSourcedEntity: EventSourcedEntity =>
        eventSourcedEntitySource(service, eventSourcedEntity, packageName, className, interfaceClassName)
      case valueEntity: ValueEntity =>
        ValueEntitySourceGenerator.valueEntitySource(service, valueEntity, packageName, className)
      case replicatedEntity: ReplicatedEntity =>
        ReplicatedEntitySourceGenerator.replicatedEntitySource(service, replicatedEntity, packageName, className)
    }
  }

  private[codegen] def eventSourcedEntityRouter(
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.EventSourcedEntity,
      packageName: String,
      className: String): String = {

    val imports = generateCommandImports(
      service.commands,
      entity.state,
      packageName,
      otherImports = Seq(
        "com.akkaserverless.javasdk.eventsourcedentity.CommandContext",
        "com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity",
        "com.akkaserverless.javasdk.impl.eventsourcedentity.EventSourcedEntityRouter"))

    val stateType = entity.state.fqn.fullName

    val eventCases = {
      if (entity.events.isEmpty)
        List(s"throw new EventSourcedEntityRouter.EventHandlerNotFound(event.getClass());")
      else
        entity.events.zipWithIndex.map { case (evt, i) =>
          val eventType = evt.fqn.fullName
          s"""|${if (i == 0) "" else "} else "}if (event instanceof $eventType) {
              |  return entity().${lowerFirst(evt.fqn.name)}(state, ($eventType) event);""".stripMargin
        }.toSeq :+
        s"""|} else {
          |  throw new EventSourcedEntityRouter.EventHandlerNotFound(event.getClass());
          |}""".stripMargin
    }

    val commandCases = service.commands
      .map { cmd =>
        val methodName = cmd.name
        val inputType = cmd.inputType.fullName
        s"""|case "$methodName":
            |  return entity().${lowerFirst(methodName)}(state, ($inputType) command);
            |""".stripMargin
      }

    s"""package $packageName;
        |
        |${writeImports(imports)}
        |
        |$managedComment
        |
        |/**
        | * An event sourced entity handler that is the glue between the Protobuf service <code>${service.fqn.name}</code>
        | * and the command and event handler methods in the <code>${entity.fqn.name}</code> class.
        | */
        |public class ${className}Router extends EventSourcedEntityRouter<$stateType, ${entity.fqn.name}> {
        |
        |  public ${className}Router(${entity.fqn.name} entity) {
        |    super(entity);
        |  }
        |
        |  @Override
        |  public $stateType handleEvent($stateType state, Object event) {
        |    ${Format.indent(eventCases, 4)}
        |  }
        |
        |  @Override
        |  public EventSourcedEntity.Effect<?> handleCommand(
        |      String commandName, $stateType state, Object command, CommandContext context) {
        |    switch (commandName) {
        |
        |      ${Format.indent(commandCases, 6)}
        |
        |      default:
        |        throw new EventSourcedEntityRouter.CommandHandlerNotFound(commandName);
        |    }
        |  }
        |}
        |""".stripMargin

  }

  private[codegen] def eventSourcedEntityProvider(
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.EventSourcedEntity,
      packageName: String,
      className: String): String = {
    val relevantTypes = {
      service.commands.flatMap { cmd =>
        cmd.inputType :: cmd.outputType :: Nil
      }.toSeq :+ entity.state.fqn
    }

    implicit val imports: Imports = generateImports(
      relevantTypes ++ relevantTypes.map(_.descriptorImport),
      packageName,
      otherImports = Seq(
        "com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityContext",
        "com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityOptions",
        "com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityProvider",
        "com.google.protobuf.Descriptors",
        "java.util.function.Function"))

    val descriptors =
      (collectRelevantTypes(relevantTypes, service.fqn)
        .map(d =>
          s"${d.parent.javaOuterClassname}.getDescriptor()") :+ s"${service.fqn.parent.javaOuterClassname}.getDescriptor()").distinct.sorted

    s"""package $packageName;
        |
        |${writeImports(imports)}
        |
        |$managedComment
        |
        |/**
        | * An event sourced entity provider that defines how to register and create the entity for
        | * the Protobuf service <code>${service.fqn.name}</code>.
        | *
        | * Should be used with the <code>register</code> method in {@link com.akkaserverless.javasdk.AkkaServerless}.
        | */
        |public class ${className}Provider implements EventSourcedEntityProvider<${entity.state.fqn.fullName}, $className> {
        |
        |  private final Function<EventSourcedEntityContext, $className> entityFactory;
        |  private final EventSourcedEntityOptions options;
        |
        |  /** Factory method of ${className}Provider */
        |  public static ${className}Provider of(Function<EventSourcedEntityContext, $className> entityFactory) {
        |    return new ${className}Provider(entityFactory, EventSourcedEntityOptions.defaults());
        |  }
        |
        |  private ${className}Provider(
        |      Function<EventSourcedEntityContext, $className> entityFactory,
        |      EventSourcedEntityOptions options) {
        |    this.entityFactory = entityFactory;
        |    this.options = options;
        |  }
        |
        |  @Override
        |  public final EventSourcedEntityOptions options() {
        |    return options;
        |  }
        |
        |  public final ${className}Provider withOptions(EventSourcedEntityOptions options) {
        |    return new ${className}Provider(entityFactory, options);
        |  }
        |
        |  @Override
        |  public final Descriptors.ServiceDescriptor serviceDescriptor() {
        |    return ${typeName(service.fqn.descriptorImport)}.getDescriptor().findServiceByName("${service.fqn.name}");
        |  }
        |
        |  @Override
        |  public final String entityType() {
        |    return "${entity.entityType}";
        |  }
        |
        |  @Override
        |  public final ${className}Router newRouter(EventSourcedEntityContext context) {
        |    return new ${className}Router(entityFactory.apply(context));
        |  }
        |
        |  @Override
        |  public final Descriptors.FileDescriptor[] additionalDescriptors() {
        |    return new Descriptors.FileDescriptor[] {
        |      ${Format.indent(descriptors.mkString(",\n"), 6)}
        |    };
        |  }
        |}
        |""".stripMargin

  }

  private[codegen] def eventSourcedEntitySource(
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.EventSourcedEntity,
      packageName: String,
      className: String,
      interfaceClassName: String): String = {
    val messageTypes = service.commands.toSeq
      .flatMap(command => Seq(command.inputType, command.outputType)) ++
      entity.events.map(_.fqn) :+ entity.state.fqn

    val imports = generateCommandImports(
      service.commands,
      entity.state,
      packageName,
      Seq(
        "com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityContext",
        "com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity",
        "com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity.Effect"))

    val commandHandlers =
      service.commands
        .map { command =>
          s"""|@Override
              |public Effect<${qualifiedType(command.outputType)}> ${lowerFirst(command.name)}(${qualifiedType(
            entity.state.fqn)} currentState, ${qualifiedType(command.inputType)} ${lowerFirst(command.inputType.name)}) {
              |  return effects().error("The command handler for `${command.name}` is not implemented, yet");
              |}
              |""".stripMargin
        }

    val eventHandlers =
      entity match {
        case ModelBuilder.EventSourcedEntity(_, _, _, events) =>
          events.map { event =>
            s"""|@Override
                |public ${qualifiedType(entity.state.fqn)} ${lowerFirst(event.fqn.name)}(${qualifiedType(
              entity.state.fqn)} currentState, ${qualifiedType(event.fqn)} ${lowerFirst(event.fqn.name)}) {
                |  throw new RuntimeException("The event handler for `${event.fqn.name}` is not implemented, yet");
                |}""".stripMargin
          }
      }

    s"""package $packageName;
       |
       |${writeImports(imports)}
       |
       |$unmanagedComment
       |
       |/** An event sourced entity. */
       |public class $className extends ${interfaceClassName} {
       |
       |  @SuppressWarnings("unused")
       |  private final String entityId;
       |
       |  public $className(EventSourcedEntityContext context) {
       |    this.entityId = context.entityId();
       |  }
       |
       |  @Override
       |  public ${qualifiedType(entity.state.fqn)} emptyState() {
       |    throw new UnsupportedOperationException("Not implemented yet, replace with your empty entity state");
       |  }
       |
       |  ${Format.indent(commandHandlers, num = 2)}
       |
       |  ${Format.indent(eventHandlers, num = 2)}
       |
       |}
       |""".stripMargin
  }

  private[codegen] def interfaceSource(
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.Entity,
      packageName: String,
      className: String): String =
    entity match {
      case eventSourcedEntity: ModelBuilder.EventSourcedEntity =>
        abstractEventSourcedEntity(service, eventSourcedEntity, packageName, className)
      case valueEntity: ModelBuilder.ValueEntity =>
        ValueEntitySourceGenerator.abstractValueEntity(service, valueEntity, packageName, className)
      case replicatedEntity: ReplicatedEntity =>
        ReplicatedEntitySourceGenerator.abstractReplicatedEntity(service, replicatedEntity, packageName, className)
    }

  private[codegen] def routerSource(
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.Entity,
      packageName: String,
      className: String): String = {
    entity match {
      case entity: ModelBuilder.EventSourcedEntity =>
        EntityServiceSourceGenerator.eventSourcedEntityRouter(service, entity, packageName, className)
      case entity: ValueEntity =>
        ValueEntitySourceGenerator.valueEntityRouter(service, entity, packageName, className)
      case entity: ReplicatedEntity =>
        ReplicatedEntitySourceGenerator.replicatedEntityRouter(service, entity, packageName, className)
    }
  }

  private[codegen] def providerSource(
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.Entity,
      packageName: String,
      className: String): String = {
    entity match {
      case eventSourcedEntity: ModelBuilder.EventSourcedEntity =>
        eventSourcedEntityProvider(service, eventSourcedEntity, packageName, className)
      case valueEntity: ValueEntity =>
        ValueEntitySourceGenerator.valueEntityProvider(service, valueEntity, packageName, className)
      case replicatedEntity: ReplicatedEntity =>
        ReplicatedEntitySourceGenerator.replicatedEntityProvider(service, replicatedEntity, packageName, className)
    }
  }

  private[codegen] def abstractEventSourcedEntity(
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.EventSourcedEntity,
      packageName: String,
      className: String): String = {
    val imports = generateCommandImports(
      service.commands,
      entity.state,
      packageName,
      Seq("com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity"))

    val commandHandlers = service.commands.map { command =>
      s"""|/** Command handler for "${command.name}". */
          |public abstract Effect<${qualifiedType(command.outputType)}> ${lowerFirst(command.name)}(${qualifiedType(
        entity.state.fqn)} currentState, ${qualifiedType(command.inputType)} ${lowerFirst(command.inputType.name)});
         |""".stripMargin
    }

    val eventHandlers = entity.events.map { event =>
      s"""|/** Event handler for "${event.fqn.name}". */
          |public abstract ${qualifiedType(entity.state.fqn)} ${lowerFirst(event.fqn.name)}(${qualifiedType(
        entity.state.fqn)} currentState, ${qualifiedType(event.fqn)} ${lowerFirst(event.fqn.name)});
         |""".stripMargin
    }

    s"""package $packageName;
        |
        |${writeImports(imports)}
        |
        |$managedComment
        |
        |/** An event sourced entity. */
        |public abstract class Abstract${className} extends EventSourcedEntity<${qualifiedType(entity.state.fqn)}> {
        |
        |  ${Format.indent(commandHandlers, num = 2)}
        |
        |  ${Format.indent(eventHandlers, num = 2)}
        |
        |}""".stripMargin
  }

  private[codegen] def integrationTestSource(
      mainClassPackageName: String,
      mainClassName: String,
      service: ModelBuilder.EntityService,
      entity: ModelBuilder.Entity,
      packageName: String,
      testClassName: String): String = {
    val serviceName = service.fqn.name

    val importTypes = commandTypes(service.commands) ++
      (entity match {
        case ModelBuilder.EventSourcedEntity(_, _, state, _) => Seq(state.fqn)
        case v: ModelBuilder.ValueEntity                     => Seq(v.state.fqn)
        case ModelBuilder.ReplicatedEntity(_, _, data) =>
          data.typeArguments.collect { case MessageTypeArgument(fqn) => fqn }
      })

    val extraImports = entity match {
      case ModelBuilder.ReplicatedEntity(_, _, data) =>
        extraReplicatedImports(data) ++ extraTypeImports(data.typeArguments)
      case _ => Seq.empty
    }

    val imports = generateImports(
      importTypes,
      packageName,
      List(service.fqn.parent.javaPackage + "." + serviceName) ++
      Seq(
        "com.akkaserverless.javasdk.testkit.junit.AkkaServerlessTestKitResource",
        "org.junit.ClassRule",
        "org.junit.Test",
        mainClassPackageName + "." + mainClassName) ++ extraImports)

    val testCases = service.commands.map { command =>
      s"""|@Test
          |public void ${lowerFirst(command.name)}OnNonExistingEntity() throws Exception {
          |  // TODO: set fields in command, and provide assertions to match replies
          |  // client.${lowerFirst(command.name)}(${qualifiedType(command.inputType)}.newBuilder().build())
          |  //         .toCompletableFuture().get(5, SECONDS);
          |}
          |""".stripMargin

    }

    s"""package $packageName;
      |
      |${writeImports(imports)}
      |
      |import static java.util.concurrent.TimeUnit.*;
      |
      |$unmanagedComment
      |
      |// Example of an integration test calling our service via the Akka Serverless proxy
      |// Run all test classes ending with "IntegrationTest" using `mvn verify -Pit`
      |public class $testClassName {
      |
      |  /**
      |   * The test kit starts both the service container and the Akka Serverless proxy.
      |   */
      |  @ClassRule
      |  public static final AkkaServerlessTestKitResource testKit =
      |    new AkkaServerlessTestKitResource(${mainClassName}.createAkkaServerless());
      |
      |  /**
      |   * Use the generated gRPC client to call the service through the Akka Serverless proxy.
      |   */
      |  private final $serviceName client;
      |
      |  public $testClassName() {
      |    client = testKit.getGrpcClient($serviceName.class);
      |  }
      |
      |  ${Format.indent(testCases, num = 2)}
      |}
      |""".stripMargin
  }

}
