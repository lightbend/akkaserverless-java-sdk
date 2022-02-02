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

import java.nio.file.Path
import java.nio.file.Paths

import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.control.NoStackTrace

import com.lightbend.akkasls.codegen.ModelBuilder.Command
import com.lightbend.akkasls.codegen.ModelBuilder.MessageTypeArgument
import com.lightbend.akkasls.codegen.ModelBuilder.ReplicatedCounterMap
import com.lightbend.akkasls.codegen.ModelBuilder.ReplicatedMap
import com.lightbend.akkasls.codegen.ModelBuilder.ReplicatedMultiMap
import com.lightbend.akkasls.codegen.ModelBuilder.ReplicatedRegister
import com.lightbend.akkasls.codegen.ModelBuilder.ReplicatedRegisterMap
import com.lightbend.akkasls.codegen.ModelBuilder.ReplicatedSet
import com.lightbend.akkasls.codegen.ModelBuilder.ScalarType
import com.lightbend.akkasls.codegen.ModelBuilder.ScalarTypeArgument
import com.lightbend.akkasls.codegen.ModelBuilder.TypeArgument

object SourceGeneratorUtils {
  val managedComment =
    """// This code is managed by Akka Serverless tooling.
       |// It will be re-generated to reflect any changes to your protobuf definitions.
       |// DO NOT EDIT""".stripMargin
  //TODO delete this val
  val unmanagedComment =
    """// This class was initially generated based on the .proto definition by Akka Serverless tooling.
       |//
       |// As long as this file exists it will not be overwritten: you can maintain it yourself,
       |// or delete it so it is regenerated as needed.""".stripMargin

  def unmanagedComment(service: Either[ModelBuilder.Service, ModelBuilder.Entity]) = {

    val (kind, messageType) = service match {
      case Left(serv: ModelBuilder.ActionService)      => ("Action Service", serv.messageType)
      case Left(serv: ModelBuilder.ViewService)        => ("View Service", serv.messageType)
      case Right(ent: ModelBuilder.EventSourcedEntity) => ("Event Sourced Entity Service", ent.messageType)
      case Right(ent: ModelBuilder.ValueEntity)        => ("Value Entity Service", ent.messageType)
      case Right(ent: ModelBuilder.ReplicatedEntity)   => ("Replicated Entity Service", ent.messageType)
    }
    val fileName = messageType.parent.protoFileName
    s"""// This class was initially generated based on the .proto definition by Akka Serverless tooling.
       |// This is the implementation for the $kind described in your $fileName file.
       |//
       |// As long as this file exists it will not be overwritten: you can maintain it yourself,
       |// or delete it so it is regenerated as needed.""".stripMargin
  }

  def mainPackageName(classNames: Iterable[String]): List[String] = {
    val packages = classNames
      .map(
        _.replaceFirst("\\.[^.]*$", "")
          .split("\\.")
          .toList)
      .toSet
    if (packages.isEmpty) throw new IllegalArgumentException("Nothing to generate!")
    longestCommonPrefix(packages.head, packages.tail) match {
      case Nil =>
        val packageNames = packages.map(_.mkString("."))
        throw new RuntimeException(
          s"No common package prefix found for protobuf packages [${packageNames.mkString(", ")}]. " +
          "Auto-selection of root package for shared generated classes not possible, please configure explicit root package for " +
          "Akka Serverless code generation in your sbt build using the Akka Serverless sbt plugin setting 'rootPackage', " +
          """for example: 'rootPackage := Some("com.example")'""") with NoStackTrace
      case prefix => prefix
    }

  }

  @tailrec
  def longestCommonPrefix(
      reference: List[String],
      others: Set[List[String]],
      resultSoFar: List[String] = Nil): List[String] = {
    reference match {
      case Nil =>
        resultSoFar
      case head :: tail =>
        if (others.forall(p => p.headOption.contains(head)))
          longestCommonPrefix(tail, others.map(_.tail), resultSoFar :+ head)
        else
          resultSoFar
    }

  }

  def disassembleClassName(fullClassName: String): (String, String) = {
    val className = fullClassName.reverse.takeWhile(_ != '.').reverse
    val packageName = fullClassName.dropRight(className.length + 1)
    packageName -> className
  }

  /**
   * Returns the name of the passed MessageType.
   *
   * If it's a ProtoMessageType with java outer class, the name will be: `OuterClass.Foo` Otherwise, it will just return
   * the message name, eg: `Foo`.
   */
  def qualifiedType(messageType: MessageType): String = {
    messageType match {
      case proto: ProtoMessageType =>
        if (proto.parent.javaMultipleFiles) messageType.name
        else s"${proto.parent.javaOuterClassname}.${messageType.name}"
      case _ => messageType.name
    }
  }

  def typeImport(messageType: MessageType): String = {
    val name =
      messageType match {
        case proto: ProtoMessageType =>
          if (proto.parent.javaMultipleFiles) proto.name
          else if (proto.parent.javaOuterClassnameOption.nonEmpty) proto.parent.javaOuterClassname
          else proto.name

        case _ => messageType.name
      }

    s"${messageType.packageName}.$name"
  }

  def lowerFirst(text: String): String =
    text.headOption match {
      case Some(c) => c.toLower.toString + text.drop(1)
      case None    => ""
    }

  def packageAsPath(packageName: String): Path =
    Paths.get(packageName.replace(".", "/"))

  /**
   * Given a Service and an Entity, return all MessageType for all possible messages:
   *   - commands for all cases
   *   - state for Value Entities
   *   - state and events for Event Sourced Entities
   *   - Value and eventually Key types for Replicated Entities (when applicable)
   */
  def allRelevantMessageTypes(service: ModelBuilder.EntityService, entity: ModelBuilder.Entity) = {

    val allCommands = service.commands.toSeq
      .flatMap(command => Seq(command.inputType, command.outputType))

    val entitySpecificMessages =
      entity match {
        case es: ModelBuilder.EventSourcedEntity =>
          es.events.map(_.messageType).toSeq :+ es.state.messageType
        case va: ModelBuilder.ValueEntity =>
          Seq(va.state.messageType)
        case re: ModelBuilder.ReplicatedEntity =>
          re.data match {
            case ReplicatedRegister(MessageTypeArgument(valueFqn))   => Seq(valueFqn)
            case ReplicatedSet(MessageTypeArgument(valueFqn))        => Seq(valueFqn)
            case ReplicatedMap(MessageTypeArgument(valueFqn))        => Seq(valueFqn)
            case ReplicatedCounterMap(MessageTypeArgument(valueFqn)) => Seq(valueFqn)

            case ReplicatedRegisterMap(MessageTypeArgument(keyFqn), MessageTypeArgument(valFqn)) => Seq(keyFqn, valFqn)
            case ReplicatedRegisterMap(MessageTypeArgument(keyFqn), _)                           => Seq(keyFqn)
            case ReplicatedRegisterMap(_, MessageTypeArgument(valueFqn))                         => Seq(valueFqn)

            case ReplicatedMultiMap(MessageTypeArgument(keyFqn), MessageTypeArgument(valFqn)) => Seq(keyFqn, valFqn)
            case ReplicatedMultiMap(MessageTypeArgument(keyFqn), _)                           => Seq(keyFqn)
            case ReplicatedMultiMap(_, MessageTypeArgument(valueFqn))                         => Seq(valueFqn)
            case _                                                                            => Seq.empty
          }
      }
    allCommands ++ entitySpecificMessages
  }

  def generateImports(
      types: Iterable[MessageType],
      packageName: String,
      otherImports: Seq[String],
      packageImports: Seq[String] = Seq.empty): Imports = {

    val messageTypeImports = types
      .filterNot(_.packageName == packageName)
      .filterNot(_.packageName.isEmpty)
      .filterNot(typ => packageImports.contains(typ.packageName))
      .map(typeImport)

    // non proto messages will require json serializers
    val importsForJsonSerializer =
      if (types.exists(_.isInstanceOf[ClassMessageType])) {
        Seq("com.akkaserverless.javasdk.impl.Serializers", "com.akkaserverless.javasdk.impl.JsonSerializer")
      } else
        Seq.empty[String]

    new Imports(
      packageName,
      (messageTypeImports ++ otherImports ++ importsForJsonSerializer ++ packageImports).toSeq.distinct.sorted)
  }

  def generateCommandAndTypeArgumentImports(
      commands: Iterable[Command],
      typeArguments: Iterable[TypeArgument],
      packageName: String,
      otherImports: Seq[String],
      packageImports: Seq[String] = Seq.empty): Imports = {

    val types = commandTypes(commands) ++
      typeArguments.collect { case MessageTypeArgument(messageType) => messageType }

    generateImports(types, packageName, otherImports ++ extraTypeImports(typeArguments), packageImports)
  }

  def generateSerializers(classMessageTypes: Iterable[ClassMessageType]): String =
    if (classMessageTypes.isEmpty) {
      "Serializer.noopSerializer()"
    } else {
      val buffer =
        classMessageTypes.foldLeft(new StringBuilder("Serializers\n")) { case (buff, domainType) =>
          buff.append(s".add(new JsonSerializer(${domainType.name}.class))\n")
        }
      buffer.toString()
    }

  def extraTypeImports(typeArguments: Iterable[TypeArgument]): Seq[String] =
    typeArguments.collect { case ScalarTypeArgument(ScalarType.Bytes) =>
      "com.google.protobuf.ByteString"
    }.toSeq

  def commandTypes(commands: Iterable[Command]): Seq[ProtoMessageType] =
    commands.flatMap(command => Seq(command.inputType, command.outputType)).toSeq

  def collectRelevantTypes(
      messageTypes: Iterable[ProtoMessageType],
      service: ProtoMessageType): immutable.Seq[ProtoMessageType] = {
    messageTypes.filterNot { messageType =>
      messageType.parent == service.parent
    }.toList
  }

  def collectRelevantTypeDescriptors(messageTypes: Iterable[ProtoMessageType], service: ProtoMessageType): String = {
    collectRelevantTypes(messageTypes, service)
      .map(desc => s"${desc.parent.javaOuterClassname}.getDescriptor()")
      .distinct
      .sorted
      .mkString(",\n")
  }

  def extraReplicatedImports(replicatedData: ModelBuilder.ReplicatedData): Seq[String] = {
    replicatedData match {
      // special case ReplicatedMap as heterogeneous with ReplicatedData values
      case _: ModelBuilder.ReplicatedMap => Seq("com.akkaserverless.replicatedentity.ReplicatedData")
      case _                             => Seq.empty
    }
  }

  @tailrec
  def dotsToCamelCase(s: String, resultSoFar: String = "", capitalizeNext: Boolean = true): String = {
    if (s.isEmpty) resultSoFar
    else if (s.head == '.' || s.head == '_' || s.head == '-') dotsToCamelCase(s.tail, resultSoFar, true)
    else if (capitalizeNext) dotsToCamelCase(s.tail, resultSoFar + s.head.toUpper, false)
    else dotsToCamelCase(s.tail, resultSoFar + s.head, false)
  }

  sealed trait CodeElement
  object CodeElement {
    case class StringElement(string: String) extends CodeElement
    case class MessageTypeElement(messageType: MessageType) extends CodeElement

    implicit def stringElement(string: String): CodeElement = StringElement(string)
    implicit def messageTypeElement(messageType: MessageType): CodeElement = MessageTypeElement(messageType)
    implicit def block(els: Iterable[CodeElement]): CodeElement =
      CodeBlock(
        // add line terminator between each two elements
        els.toVector.flatMap(Seq(_, stringElement("\n"))).dropRight(1))
  }
  case class CodeBlock(code: Seq[CodeElement]) extends CodeElement {
    import CodeElement._

    /** All classes used in this code block */
    def messageTypes: Seq[MessageType] = code.flatMap {
      case MessageTypeElement(name) => Seq(name)
      case block: CodeBlock         => block.messageTypes
      case _: StringElement         => Seq.empty
    }

    def write(imports: Imports, typeName: MessageType => String): String =
      code.foldLeft("")((acc, o) =>
        o match {
          case StringElement(s) =>
            acc ++ s
          case block: CodeBlock =>
            val currentIndent = lastIndentRegex.findFirstMatchIn(acc).get.matched
            acc ++ block.write(imports, typeName).replaceAll("\n", "\n" + currentIndent)
          case MessageTypeElement(messageType) =>
            acc ++ typeName(messageType)
        })
  }

  implicit class CodeBlockHelper(val sc: StringContext) extends AnyVal {
    def c(args: CodeElement*): CodeBlock =
      CodeBlock(interleave(sc.parts.map(_.stripMargin), args))

    private def interleave(strings: Seq[String], values: Seq[CodeElement]): Seq[CodeElement] =
      values.headOption match {
        case Some(value) => Seq(CodeElement.StringElement(strings.head), value) ++ interleave(strings.tail, values.tail)
        case None =>
          require(strings.size == 1)
          strings.map(CodeElement.StringElement)
      }
  }

  private val lastIndentRegex = "[ \t]*$".r
}
