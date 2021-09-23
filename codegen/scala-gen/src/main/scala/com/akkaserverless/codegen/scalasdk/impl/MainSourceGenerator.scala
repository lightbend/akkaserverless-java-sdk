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
import com.lightbend.akkasls.codegen.ModelBuilder.Entity
import com.lightbend.akkasls.codegen.ModelBuilder.Service
import com.lightbend.akkasls.codegen._

/**
 * Responsible for generating Main and AkkaServerlessFactory Java source from an entity model
 */
object MainSourceGenerator {

  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._

  // FIXME
  def excludedUntilImplemented(model: ModelBuilder.Model): ModelBuilder.Model = {
    val filteredServices = model.services.flatMap {
      case (name, service: ModelBuilder.EntityService) =>
        model.lookupEntity(service) match {
          case _: ModelBuilder.ValueEntity =>
            // List(name -> service)
            Nil
          case _: ModelBuilder.EventSourcedEntity =>
            Nil
          case _: ModelBuilder.ReplicatedEntity =>
            Nil
        }
      case (name, service: ModelBuilder.ViewService)   => List(name -> service)
      case (name, service: ModelBuilder.ActionService) => List(name -> service)
    }

    val filteredEntities = model.entities.filter {
      case (_, _: ModelBuilder.ValueEntity)        => true
      case (_, _: ModelBuilder.EventSourcedEntity) => false
      case (_, _: ModelBuilder.ReplicatedEntity)   => false
    }

    ModelBuilder.Model(filteredServices, filteredEntities)
  }

  def generateUnmanaged(model: ModelBuilder.Model): Iterable[File] = {
    val filteredModel = excludedUntilImplemented(model) // FIXME remove
    val generatedSources = Seq.newBuilder[File]

    val mainPackage = mainPackageName(filteredModel.services.keys ++ filteredModel.entities.keys)
    val mainPackageStr = mainPackage.mkString(".")
    val mainPackagePath = mainPackage.mkString("/")
    val mainClassName = "Main"

    generatedSources += File(
      s"$mainPackagePath/$mainClassName.scala",
      mainSource(mainPackageStr, mainClassName, filteredModel.entities, filteredModel.services))

    generatedSources.result()
  }

  def generateManaged(model: ModelBuilder.Model): Iterable[File] = {
    val filteredModel = excludedUntilImplemented(model) // FIXME remove
    val generatedSources = Seq.newBuilder[File]

    val mainPackage = mainPackageName(filteredModel.services.keys ++ filteredModel.entities.keys)
    val mainPackageStr = mainPackage.mkString(".")
    val mainPackagePath = mainPackage.mkString("/")

    generatedSources += File(
      s"$mainPackagePath/AkkaServerlessFactory.scala",
      akkaServerlessFactorySource(mainPackageStr, filteredModel))

    generatedSources.result()
  }

  private[codegen] def mainSource(
      mainClassPackageName: String,
      mainClassName: String,
      entities: Map[String, Entity],
      services: Map[String, Service]): String = {

    val entityImports = entities.values.collect {
      case entity: ModelBuilder.EventSourcedEntity => entity.fqn.fullQualifiedName
      case entity: ModelBuilder.ValueEntity        => entity.fqn.fullQualifiedName
      case entity: ModelBuilder.ReplicatedEntity   => entity.fqn.fullQualifiedName
    }.toSeq

    val serviceImports = services.values.collect {
      case service: ModelBuilder.ActionService => service.classNameQualified
      case view: ModelBuilder.ViewService      => view.classNameQualified
    }.toSeq

    val allImports = entityImports ++ serviceImports ++
      List("com.akkaserverless.scalasdk.AkkaServerless", "org.slf4j.LoggerFactory")

    val imports =
      generateImports(Iterable.empty, mainClassPackageName, allImports, semi = false)

    val entityRegistrationParameters = entities.values.toList
      .sortBy(_.fqn.name)
      .collect {
        case entity: ModelBuilder.EventSourcedEntity => s"new ${entity.fqn.name}(_)"
        case entity: ModelBuilder.ValueEntity        => s"new ${entity.fqn.name}(_)"
        case entity: ModelBuilder.ReplicatedEntity   => s"new ${entity.fqn.name}(_)"
      }

    val serviceRegistrationParameters = services.values.toList
      .sortBy(_.fqn.name)
      .collect {
        case service: ModelBuilder.ActionService => s"new ${service.className}(_)"
        case view: ModelBuilder.ViewService      => s"new ${view.className}(_)"
      }

    val registrationParameters = entityRegistrationParameters ::: serviceRegistrationParameters
    s"""|package $mainClassPackageName
        |
        |$imports
        |
        |object $mainClassName {
        |
        |  private val log = LoggerFactory.getLogger("$mainClassPackageName.$mainClassName")
        |
        |  def createAkkaServerless(): AkkaServerless = {
        |    // The AkkaServerlessFactory automatically registers any generated Actions, Views or Entities,
        |    // and is kept up-to-date with any changes in your protobuf definitions.
        |    // If you prefer, you may remove this and manually register these components in a
        |    // `AkkaServerless()` instance.
        |    AkkaServerlessFactory.withComponents(
        |      ${registrationParameters.mkString(",\n      ")})
        |  }
        |
        |  def main(args: Array[String]): Unit = {
        |    log.info("starting the Akka Serverless service")
        |    createAkkaServerless().start()
        |  }
        |}
        |""".stripMargin

  }

  private[codegen] def akkaServerlessFactorySource(mainClassPackageName: String, model: ModelBuilder.Model): String = {
    val registrations = model.services.values
      .flatMap {
        case service: ModelBuilder.EntityService =>
          model.entities.get(service.componentFullName).toSeq.map {
            case entity: ModelBuilder.EventSourcedEntity =>
              s".register(${entity.fqn.name}Provider(create${entity.fqn.name}))"
            case entity: ModelBuilder.ValueEntity =>
              s".register(${entity.fqn.name}Provider(create${entity.fqn.name}))"
            case entity: ModelBuilder.ReplicatedEntity =>
              s".register(${entity.fqn.name}Provider(create${entity.fqn.name}))"
          }

        case service: ModelBuilder.ViewService =>
          List(s".register(${service.providerName}(create${service.className}))")

        case service: ModelBuilder.ActionService =>
          List(s".register(${service.providerName}(create${service.className}))")

      }
      .toList
      .sorted

    val entityImports = model.entities.values.flatMap { ety =>
      val imp =
        ety.fqn.fullQualifiedName :: Nil
      ety match {
        case _: ModelBuilder.EventSourcedEntity =>
          s"${ety.fqn.fullQualifiedName}Provider" :: imp
        case _: ModelBuilder.ValueEntity =>
          s"${ety.fqn.fullQualifiedName}Provider" :: imp
        case _: ModelBuilder.ReplicatedEntity =>
          s"${ety.fqn.fullQualifiedName}Provider" :: imp
        case _ => imp
      }
    }

    val serviceImports = model.services.values.flatMap { serv =>
      serv match {
        case actionServ: ModelBuilder.ActionService =>
          List(actionServ.classNameQualified, actionServ.providerNameQualified)
        case view: ModelBuilder.ViewService =>
          List(view.classNameQualified, view.providerNameQualified)
        case _ => Nil
      }
    }

    val entityContextImports = model.entities.values.collect {
      case _: ModelBuilder.EventSourcedEntity =>
        List("com.akkaserverless.scalasdk.eventsourcedentity.EventSourcedEntityContext")
      case _: ModelBuilder.ValueEntity =>
        List("com.akkaserverless.scalasdk.valueentity.ValueEntityContext")
      case _: ModelBuilder.ReplicatedEntity =>
        List("com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntityContext")
    }.flatten

    val serviceContextImports = model.services.values.collect {
      case _: ModelBuilder.ActionService =>
        List("com.akkaserverless.scalasdk.action.ActionCreationContext")
      case _: ModelBuilder.ViewService =>
        List("com.akkaserverless.scalasdk.view.ViewCreationContext")
    }.flatten

    val allImports = (entityImports ++ serviceImports ++ entityContextImports ++ serviceContextImports ++
      List("com.akkaserverless.scalasdk.AkkaServerless")).toList

    val imports =
      generateImports(Iterable.empty, mainClassPackageName, allImports, semi = false)

    val entityCreators =
      model.entities.values.toList
        .sortBy(_.fqn.name)
        .collect {
          case entity: ModelBuilder.EventSourcedEntity =>
            s"create${entity.fqn.name}: Function[EventSourcedEntityContext, ${entity.fqn.name}]"
          case entity: ModelBuilder.ValueEntity =>
            s"create${entity.fqn.name}: Function[ValueEntityContext, ${entity.fqn.name}]"
          case entity: ModelBuilder.ReplicatedEntity =>
            s"create${entity.fqn.name}: Function[ReplicatedEntityContext, ${entity.fqn.name}]"
        }

    val serviceCreators = model.services.values.toList
      .sortBy(_.fqn.name)
      .collect {
        case service: ModelBuilder.ActionService =>
          s"create${service.className}: Function[ActionCreationContext, ${service.className}]"
        case view: ModelBuilder.ViewService =>
          s"create${view.className}: Function[ViewCreationContext, ${view.className}]"
      }

    val creatorParameters = entityCreators ::: serviceCreators

    s"""|package $mainClassPackageName
        |
        |$imports
        |
        |object AkkaServerlessFactory {
        |
        |  def withComponents(
        |      ${creatorParameters.mkString(",\n      ")}): AkkaServerless = {
        |    val akkaServerless = AkkaServerless()
        |    akkaServerless
        |      ${Format.indent(registrations, 6)}
        |  }
        |}
        |""".stripMargin
  }

}
