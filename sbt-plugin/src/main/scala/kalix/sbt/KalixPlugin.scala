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

package kalix.sbt

import akka.grpc.sbt.AkkaGrpcPlugin
import akka.grpc.sbt.AkkaGrpcPlugin.autoImport.{ akkaGrpcCodeGeneratorSettings, akkaGrpcGeneratedSources }
import akka.grpc.sbt.AkkaGrpcPlugin.autoImport.AkkaGrpc
import java.lang.{ Boolean => JBoolean }
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

import kalix.codegen.scalasdk.{ gen, genTests, genUnmanaged, genUnmanagedTest, BuildInfo, KalixGenerator }
import sbt.{ Compile, _ }
import sbt.Keys._
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB
import kalix.devtools.impl.DockerComposeUtils
object KalixPlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = ProtocPlugin && AkkaGrpcPlugin

  trait Keys { _: autoImport.type =>
    val generateUnmanaged = taskKey[Seq[File]](
      "Generate \"unmanaged\" kalix scaffolding code based on the available .proto definitions.\n" +
      "These are the source files that are placed in the source tree, and after initial generation should typically be maintained by the user.\n" +
      "Files that already exist they are not re-generated.")
    val temporaryUnmanagedDirectory = settingKey[File]("Directory to generate 'unmanaged' sources into")
    val onlyUnitTest = settingKey[Boolean]("Filters out integration tests. By default: false")
    val protobufDescriptorSetOut = settingKey[File]("The file to write the descriptor set to")
    val rootPackage = settingKey[Option[String]](
      "A root scala package to use for generated common classes such as Main, by default auto detected from protobuf files")

    val dockerComposeFile = settingKey[String]("Path to docker compose file. Default to docker-compose.yml")

    val logConfig = settingKey[String]("The log config to use. Default to logback-dev-mode.xml")
    lazy val runDockerCompose = taskKey[Unit]("Run docker compose")

  }

  // defined as key so we can init it sooner and detected errors earlier
  private val dockerComposeUtils = settingKey[DockerComposeUtils]("Docker compose utils")

  object autoImport extends Keys
  import autoImport._

  val KalixSdkVersion = BuildInfo.version
  val KalixProtocolVersion = BuildInfo.protocolVersion

  override def projectSettings = {
    addCommandAlias("runAll", "runDockerCompose;run") ++
    Seq(
      dockerComposeFile := "docker-compose.yml",
      dockerComposeUtils := DockerComposeUtils(dockerComposeFile.value, sys.env),
//      runDockerCompose := dockerComposeUtils.start(),
      libraryDependencies ++= Seq(
        "io.kalix" % "kalix-sdk-protocol" % KalixProtocolVersion % "protobuf-src",
        "com.google.protobuf" % "protobuf-java" % "3.17.3" % "protobuf",
        "io.kalix" %% "kalix-scala-sdk-protobuf-testkit" % KalixSdkVersion % Test),
      logConfig := "logback-dev-mode.xml",
      run / javaOptions ++=
//        dockerComposeUtils.value.readServicePortMappings
        Seq(
          "-Dkalix.user-function-interface=0.0.0.0",
          s"-Dlogback.configurationFile=${logConfig.value}"
//          s"-Dkalix.user-function-port=${dockerComposeUtils.value.readUserFunctionPort}"
        ),
      Compile / PB.targets +=
        gen(
          (akkaGrpcCodeGeneratorSettings.value :+ KalixGenerator.enableDebug)
          ++ rootPackage.value.map(KalixGenerator.rootPackage)) -> (Compile / sourceManaged).value,
      Compile / temporaryUnmanagedDirectory := (Compile / crossTarget).value / "kalix-unmanaged",
      Test / temporaryUnmanagedDirectory := (Test / crossTarget).value / "kalix-unmanaged-test",
      protobufDescriptorSetOut := (Compile / resourceManaged).value / "protobuf" / "descriptor-sets" / "user-function.desc",
      rootPackage := None,
      // FIXME there is a name clash between the Akka gRPC server-side service 'handler'
      // and the Kalix 'handler'. For now working around it by only generating
      // the client, but we should probably resolve this before the first public release.
      Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
      Compile / PB.targets ++= Seq(
        genUnmanaged((akkaGrpcCodeGeneratorSettings.value :+ KalixGenerator.enableDebug)
        ++ rootPackage.value.map(KalixGenerator.rootPackage)) -> (Compile / temporaryUnmanagedDirectory).value),
      Compile / PB.generate := (Compile / PB.generate)
        .dependsOn(Def.task {
          protobufDescriptorSetOut.value.getParentFile.mkdirs()
        })
        .value,
      Compile / PB.protocOptions ++= Seq(
        "--descriptor_set_out",
        protobufDescriptorSetOut.value.getAbsolutePath,
        "--include_source_info"),
      Test / PB.targets ++= Seq(
        genUnmanagedTest((akkaGrpcCodeGeneratorSettings.value :+ KalixGenerator.enableDebug)
        ++ rootPackage.value.map(KalixGenerator.rootPackage)) -> (Test / temporaryUnmanagedDirectory).value),
      Test / PB.protoSources ++= (Compile / PB.protoSources).value,
      Test / PB.targets +=
        genTests(
          (akkaGrpcCodeGeneratorSettings.value :+ KalixGenerator.enableDebug)
          ++ rootPackage.value.map(KalixGenerator.rootPackage)) -> (Test / sourceManaged).value,
      Compile / generateUnmanaged := {
        Files.createDirectories(Paths.get((Compile / temporaryUnmanagedDirectory).value.toURI))
        // Make sure generation has happened
        val _ = (Compile / PB.generate).value
        // Then copy over any new generated unmanaged sources
        copyIfNotExist(
          Paths.get((Compile / temporaryUnmanagedDirectory).value.toURI),
          Paths.get((Compile / sourceDirectory).value.toURI).resolve("scala"))
      },
      Test / generateUnmanaged := {
        Files.createDirectories(Paths.get((Test / temporaryUnmanagedDirectory).value.toURI))
        // Make sure generation has happened
        val _ = (Test / PB.generate).value
        // Then copy over any new generated unmanaged sources
        copyIfNotExist(
          Paths.get((Test / temporaryUnmanagedDirectory).value.toURI),
          Paths.get((Test / sourceDirectory).value.toURI).resolve("scala"))
      },
      Compile / managedSources :=
        (Compile / managedSources).value.filter(s => !isIn(s, (Compile / temporaryUnmanagedDirectory).value)),
      Compile / managedResources += {
        // make sure the file has been generated
        val _ = (Compile / PB.generate).value
        protobufDescriptorSetOut.value
      },
      Compile / unmanagedSources := {
        val _ = (Test / unmanagedSources).value // touch unmanaged test sources, so that they are generated on `compile`
        (Compile / generateUnmanaged).value ++ (Compile / unmanagedSources).value
      },
      Test / managedSources :=
        (Test / managedSources).value.filter(s => !isIn(s, (Test / temporaryUnmanagedDirectory).value)),
      Test / unmanagedSources :=
        (Test / generateUnmanaged).value ++ (Test / unmanagedSources).value,
      onlyUnitTest := {
        sys.props.get("onlyUnitTest") match {
          case Some("") => true
          case Some(x)  => JBoolean.valueOf(x)
          case None     => false
        }
      },
      Test / testOptions ++= {
        if (onlyUnitTest.value)
          Seq(Tests.Filter(name => !name.endsWith("IntegrationSpec")))
        else Nil
      })
  }

  def isIn(file: File, dir: File): Boolean =
    Paths.get(file.toURI).startsWith(Paths.get(dir.toURI))

  private def copyIfNotExist(from: Path, to: Path): Seq[File] = {
    Files
      .walk(from)
      .filter(Files.isRegularFile(_))
      .flatMap[File](file => {
        val target = to.resolve(from.relativize(file))
        if (!Files.exists(target)) {
          Files.createDirectories(target.getParent)
          Files.copy(file, target)
          java.util.stream.Stream.of[File](target.toFile)
        } else java.util.stream.Stream.empty()
      })
      .toArray(new Array[File](_))
      .toVector
  }

}
