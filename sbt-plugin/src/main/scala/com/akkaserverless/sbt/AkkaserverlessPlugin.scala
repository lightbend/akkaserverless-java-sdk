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

package com.akkaserverless.sbt

import akka.grpc.sbt.AkkaGrpcPlugin
import akka.grpc.sbt.AkkaGrpcPlugin.autoImport.akkaGrpcCodeGeneratorSettings

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import com.akkaserverless.codegen.scalasdk.{ gen, genTests, genUnmanaged, AkkaserverlessGenerator }
import sbt.{ Compile, _ }
import sbt.Keys._
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB
import scalapb.GeneratorOption

object AkkaserverlessPlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = ProtocPlugin && AkkaGrpcPlugin

  trait Keys { _: autoImport.type =>
    val generateUnmanaged = taskKey[Seq[File]](
      "Generate \"unmanaged\" akkaserverless scaffolding code based on the available .proto definitions.\n" +
      "These are the source files that are placed in the source tree, and after initial generation should typically be maintained by the user.\n" +
      "Files that already exist they are not re-generated.")
    val temporaryUnmanagedDirectory = settingKey[File]("Directory to generate 'unmanaged' sources into")
  }
  object autoImport extends Keys
  import autoImport._

  override def projectSettings: Seq[sbt.Setting[_]] = Seq(
    libraryDependencies ++= Seq(
      "com.akkaserverless" % "akkaserverless-sdk-protocol" % "0.7.0-beta.19" % "protobuf-src",
      "com.google.protobuf" % "protobuf-java" % "3.17.3" % "protobuf"),
    Compile / PB.targets +=
      gen(
        akkaGrpcCodeGeneratorSettings.value :+ AkkaserverlessGenerator.enableDebug) -> (Compile / sourceManaged).value / "akkaserverless",
    Compile / temporaryUnmanagedDirectory := (Compile / baseDirectory).value / "target" / "akkaserverless-unmanaged",
    Compile / PB.targets ++= Seq(genUnmanaged(
      akkaGrpcCodeGeneratorSettings.value :+ AkkaserverlessGenerator.enableDebug) -> (Compile / temporaryUnmanagedDirectory).value),
    Test / PB.protoSources ++= (Compile / PB.protoSources).value,
    Test / PB.targets +=
      genTests(
        akkaGrpcCodeGeneratorSettings.value :+ AkkaserverlessGenerator.enableDebug) -> (Test / sourceManaged).value / "akkaserverless",
    Compile / generateUnmanaged := {
      Files.createDirectories(Paths.get((Compile / temporaryUnmanagedDirectory).value.toURI))
      // Make sure generation has happened
      val _ = (Compile / PB.generate).value
      // Then copy over any new generated unmanaged sources
      copyIfNotExist(
        Paths.get((Compile / temporaryUnmanagedDirectory).value.toURI),
        Paths.get((Compile / sourceDirectory).value.toURI).resolve("scala"))
    },
    Compile / managedSources :=
      (Compile / managedSources).value.filter(s => !isIn(s, (Compile / temporaryUnmanagedDirectory).value)),
    Compile / unmanagedSources :=
      (Compile / generateUnmanaged).value ++ (Compile / unmanagedSources).value)

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
