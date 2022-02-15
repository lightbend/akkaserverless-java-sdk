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

/**
 * Responsible for generating Java source from an entity model
 */
object ActionServiceSourceGenerator {
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._
  import JavaGeneratorUtils._

  /**
   * Generate Java source from views where the target source and test source directories have no existing source.
   *
   * Also generates a main source file if it does not already exist.
   *
   * Impure.
   */
  def generate(service: ModelBuilder.ActionService, rootPackage: String): GeneratedFiles = {
    val pkg = service.messageType.parent

    GeneratedFiles.Empty
      .addManaged(File.java(pkg, service.abstractActionName, abstractActionSource(service, rootPackage)))
      .addManaged(File.java(pkg, service.routerName, actionRouter(service)))
      .addManaged(File.java(pkg, service.providerName, actionProvider(service)))
      .addUnmanaged(File.java(pkg, service.className, actionSource(service)))
  }

  private def streamImports(commands: Iterable[ModelBuilder.Command]): Seq[String] = {
    if (commands.exists(_.hasStream))
      "akka.NotUsed" :: "akka.stream.javadsl.Source" :: Nil
    else
      Nil
  }

  private[codegen] def actionSource(service: ModelBuilder.ActionService): String = {

    val packageName = service.messageType.parent.javaPackage
    val className = service.className

    val imports = generateImports(
      service.commandTypes,
      packageName,
      otherImports = Seq("com.akkaserverless.javasdk.action.ActionCreationContext") ++ streamImports(service.commands))

    val methods = service.commands.map { cmd =>
      val methodName = cmd.name
      val input = lowerFirst(cmd.inputType.name)
      val inputTypeFullName = cmd.inputType.fullName
      val outputType = cmd.outputType.fullName

      if (cmd.isUnary) {
        val jsonTopicHint = {
          // note: the somewhat funky indenting is on purpose to lf+indent only if comment present
          if (cmd.inFromTopic && cmd.inputType.fullyQualifiedProtoName == "com.google.protobuf.Any")
            """|// JSON input from a topic can be decoded using JsonSupport.decodeJson(MyClass.class, any)
               |  """.stripMargin
          else if (cmd.outToTopic && cmd.outputType.fullyQualifiedProtoName == "com.google.protobuf.Any")
            """|// JSON output to emit to a topic can be encoded using JsonSupport.encodeJson(myPojo)
               |  """.stripMargin
          else ""
        }

        s"""|@Override
            |public Effect<$outputType> ${lowerFirst(methodName)}($inputTypeFullName $input) {
            |  ${jsonTopicHint}throw new RuntimeException("The command handler for `$methodName` is not implemented, yet");
            |}""".stripMargin
      } else if (cmd.isStreamOut) {
        s"""
           |@Override
           |public Source<Effect<$outputType>, NotUsed> ${lowerFirst(methodName)}($inputTypeFullName $input) {
           |  throw new RuntimeException("The command handler for `$methodName` is not implemented, yet");
           |}""".stripMargin
      } else if (cmd.isStreamIn) {
        s"""
           |@Override
           |public Effect<$outputType> ${lowerFirst(methodName)}(Source<$inputTypeFullName, NotUsed> ${input}Src) {
           |  throw new RuntimeException("The command handler for `$methodName` is not implemented, yet");
           |}""".stripMargin
      } else {
        s"""
           |@Override
           |public Source<Effect<$outputType>, NotUsed> ${lowerFirst(methodName)}(Source<$inputTypeFullName, NotUsed> ${input}Src) {
           |  throw new RuntimeException("The command handler for `$methodName` is not implemented, yet");
           |}""".stripMargin
      }
    }

    s"""|package $packageName;
        |
        |${writeImports(imports)}
        |
        |${unmanagedComment(Left(service))}
        |
        |public class $className extends ${service.abstractActionName} {
        |
        |  public $className(ActionCreationContext creationContext) {}
        |
        |  ${Format.indent(methods, 2)}
        |}
        |""".stripMargin
  }

  private[codegen] def abstractActionSource(service: ModelBuilder.ActionService, rootPackage: String): String = {

    val packageName = service.messageType.parent.javaPackage
    val imports = generateImports(
      service.commandTypes,
      packageName,
      otherImports = streamImports(service.commands) ++
        Seq(s"$rootPackage.Components", s"$rootPackage.ComponentsImpl"))

    val methods = service.commands.map { cmd =>
      val methodName = cmd.name
      val input = lowerFirst(cmd.inputType.name)
      val inputTypeFullName = cmd.inputType.fullName
      val outputType = cmd.outputType.fullName

      if (cmd.isUnary) {
        s"""|public abstract Effect<$outputType> ${lowerFirst(methodName)}($inputTypeFullName $input);""".stripMargin
      } else if (cmd.isStreamOut) {
        s"""
           |public abstract Source<Effect<$outputType>, NotUsed> ${lowerFirst(
          methodName)}($inputTypeFullName $input);""".stripMargin
      } else if (cmd.isStreamIn) {
        s"""
           |public abstract Effect<$outputType> ${lowerFirst(
          methodName)}(Source<$inputTypeFullName, NotUsed> ${input}Src);""".stripMargin
      } else {
        s"""
           |public abstract Source<Effect<$outputType>, NotUsed> ${lowerFirst(
          methodName)}(Source<$inputTypeFullName, NotUsed> ${input}Src);""".stripMargin
      }
    }

    s"""package $packageName;
        |
        |${writeImports(imports)}
        |
        |$managedComment
        |
        |public abstract class ${service.abstractActionName} extends com.akkaserverless.javasdk.action.Action {
        |
        |  protected final Components components() {
        |    return new ComponentsImpl(actionContext());
        |  }
        |
        |  ${Format.indent(methods, 2)}
        |}""".stripMargin
  }

  private[codegen] def actionRouter(service: ModelBuilder.ActionService): String = {

    val className = service.className
    val packageName = service.messageType.parent.javaPackage

    val unaryCases = service.commands.filter(_.isUnary).map { cmd =>
      val methodName = cmd.name
      val inputTypeFullName = cmd.inputType.fullName

      s"""|case "$methodName":
          |  return action()
          |           .${lowerFirst(methodName)}(($inputTypeFullName) message.payload());
          |""".stripMargin
    }

    val streamOutCases = service.commands.filter(_.isStreamOut).map { cmd =>
      val methodName = cmd.name
      val inputTypeFullName = cmd.inputType.fullName

      s"""|case "$methodName":
          |  return (Source<Effect<?>, NotUsed>)(Object) action()
          |           .${lowerFirst(methodName)}(($inputTypeFullName) message.payload());
          |""".stripMargin
    }

    val streamInCases = service.commands.filter(_.isStreamIn).map { cmd =>
      val methodName = cmd.name
      val inputTypeFullName = cmd.inputType.fullName

      s"""|case "$methodName":
          |  return action()
          |           .${lowerFirst(methodName)}(stream.map(el -> ($inputTypeFullName) el.payload()));
          |""".stripMargin
    }

    val streamInOutCases = service.commands.filter(_.isStreamInOut).map { cmd =>
      val methodName = cmd.name
      val inputTypeFullName = cmd.inputType.fullName

      s"""|case "$methodName":
          |  return (Source<Effect<?>, NotUsed>)(Object) action()
          |           .${lowerFirst(methodName)}(stream.map(el -> ($inputTypeFullName) el.payload()));
          |""".stripMargin
    }

    val imports = generateImports(
      service.commandTypes,
      packageName,
      otherImports = Seq(
        "akka.NotUsed",
        "akka.stream.javadsl.Source",
        "com.akkaserverless.javasdk.action.Action.Effect",
        "com.akkaserverless.javasdk.action.MessageEnvelope",
        "com.akkaserverless.javasdk.impl.action.ActionRouter"))

    s"""package $packageName;
        |
        |${writeImports(imports)}
        |
        |$managedComment
        |
        |public class ${service.routerName} extends ActionRouter<$className> {
        |
        |  public ${service.routerName}($className actionBehavior) {
        |    super(actionBehavior);
        |  }
        |
        |  @Override
        |  public Effect<?> handleUnary(String commandName, MessageEnvelope<Object> message) {
        |    switch (commandName) {
        |      ${Format.indent(unaryCases, 6)}
        |      default:
        |        throw new ActionRouter.HandlerNotFound(commandName);
        |    }
        |  }
        |
        |  @Override
        |  @SuppressWarnings("unchecked")
        |  public Source<Effect<?>, NotUsed> handleStreamedOut(String commandName, MessageEnvelope<Object> message) {
        |    switch (commandName) {
        |      ${Format.indent(streamOutCases, 6)}
        |      default:
        |        throw new ActionRouter.HandlerNotFound(commandName);
        |    }
        |  }
        |
        |  @Override
        |  public Effect<?> handleStreamedIn(String commandName, Source<MessageEnvelope<Object>, NotUsed> stream) {
        |    switch (commandName) {
        |      ${Format.indent(streamInCases, 6)}
        |      default:
        |        throw new ActionRouter.HandlerNotFound(commandName);
        |    }
        |  }
        |
        |  @Override
        |  @SuppressWarnings("unchecked")
        |  public Source<Effect<?>, NotUsed> handleStreamed(String commandName, Source<MessageEnvelope<Object>, NotUsed> stream) {
        |    switch (commandName) {
        |      ${Format.indent(streamInOutCases, 6)}
        |      default:
        |        throw new ActionRouter.HandlerNotFound(commandName);
        |    }
        |  }
        |}
        |""".stripMargin
  }

  private[codegen] def actionProvider(service: ModelBuilder.ActionService): String = {

    val packageName = service.messageType.parent.javaPackage
    val classNameAction = service.className
    val protoName = service.messageType.protoName

    val relevantDescriptors =
      collectRelevantTypes(service.commandTypes, service.messageType)
        .collect { case pmt: ProtoMessageType =>
          s"${pmt.parent.javaOuterClassname}.getDescriptor()"
        }

    val descriptors =
      (relevantDescriptors :+ s"${service.messageType.parent.javaOuterClassname}.getDescriptor()").distinct.sorted

    implicit val imports: Imports = generateImports(
      service.commandTypes ++ service.commandTypes.map(_.descriptorImport),
      packageName,
      otherImports = Seq(
        "com.akkaserverless.javasdk.action.ActionCreationContext",
        "com.akkaserverless.javasdk.action.ActionProvider",
        "com.akkaserverless.javasdk.action.ActionOptions",
        "com.akkaserverless.javasdk.impl.action.ActionRouter",
        "com.google.protobuf.Descriptors",
        "java.util.function.Function"))

    s"""package $packageName;
      |
      |${writeImports(imports)}
      |
      |$managedComment
      |
      |/**
      | * ${service.providerName} that defines how to register and create the action for
      | * the Protobuf service <code>$protoName</code>.
      | *
      | * Should be used with the <code>register</code> method in {@link com.akkaserverless.javasdk.AkkaServerless}.
      | */
      |public class ${service.providerName} implements ActionProvider<$classNameAction> {
      |
      |  private final Function<ActionCreationContext, $classNameAction> actionFactory;
      |  private final ActionOptions options;
      |
      |  /** Factory method of ${service.providerName} */
      |  public static ${service.providerName} of(Function<ActionCreationContext, $classNameAction> actionFactory) {
      |    return new ${service.providerName}(actionFactory, ActionOptions.defaults());
      |  }
      |
      |  private ${service.providerName}(Function<ActionCreationContext, $classNameAction> actionFactory, ActionOptions options) {
      |    this.actionFactory = actionFactory;
      |    this.options = options;
      |  }
      |
      |  @Override
      |  public final ActionOptions options() {
      |    return options;
      |  }
      |
      |  public final ${service.providerName} withOptions(ActionOptions options) {
      |    return new ${service.providerName}(actionFactory, options);
      |  }
      |
      |  @Override
      |  public final Descriptors.ServiceDescriptor serviceDescriptor() {
      |    return ${typeName(service.messageType.descriptorImport)}.getDescriptor().findServiceByName("$protoName");
      |  }
      |
      |  @Override
      |  public final ${service.routerName} newRouter(ActionCreationContext context) {
      |    return new ${service.routerName}(actionFactory.apply(context));
      |  }
      |
      |  @Override
      |  public final Descriptors.FileDescriptor[] additionalDescriptors() {
      |    return new Descriptors.FileDescriptor[] {
      |      ${Format.indent(descriptors.mkString(",\n"), 6)}
      |    };
      |  }
      |
      |}
      |""".stripMargin
  }
}
