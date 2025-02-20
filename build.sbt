import Dependencies.Kalix
import com.jsuereth.sbtpgp.PgpKeys.publishSignedConfiguration

lazy val `kalix-jvm-sdk` = project
  .in(file("."))
  .aggregate(
    devTools,
    devToolsInternal,
    coreSdk,
    javaSdkProtobuf,
    javaSdkProtobufTestKit,
    scalaSdkProtobuf,
    scalaSdkProtobufTestKit,
    javaTck,
    scalaTck,
    codegenCore,
    codegenJava,
    codegenJavaCompilationTest,
    codegenScala,
    codegenScalaCompilationTest,
    sbtPlugin)
  .settings(
    (publish / skip) := true,
    // https://github.com/sbt/sbt/issues/3465
    // Libs and plugins must share a version. The root project must use that
    // version (and set the crossScalaVersions as empty list) so each sub-project
    // can then decide which scalaVersion and crossScalaVersions they use.
    crossScalaVersions := Nil,
    scalaVersion := Dependencies.ScalaVersion)

def commonCompilerSettings: Seq[Setting[_]] =
  Seq(
    Compile / javacOptions ++= Seq("-encoding", "UTF-8"),
    Compile / scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation"))

lazy val sharedScalacOptions =
  Seq("-feature", "-unchecked")

lazy val fatalWarnings = Seq(
  "-Xfatal-warnings", // discipline only in Scala 2 for now
  "-Wconf:src=.*/target/.*:s",
  // silence warnings from generated sources
  "-Wconf:src=.*/src_managed/.*:s",
  // silence warnings from deprecated protobuf fields
  "-Wconf:src=.*/akka-grpc/.*:s")

lazy val scala212Options = sharedScalacOptions ++ fatalWarnings

lazy val scala213Options = scala212Options ++
  Seq("-Wunused:imports,privates,locals")

// -Wconf configs will be available once https://github.com/scala/scala3/pull/20282 is merged and 3.3.4 is released
lazy val scala3Options = sharedScalacOptions ++ Seq("-Wunused:imports,privates,locals")

def disciplinedScalacSettings: Seq[Setting[_]] = {
  if (sys.props.get("kalix.no-discipline").isEmpty) {
    Seq(Compile / scalacOptions ++= {
      if (scalaVersion.value.startsWith("3.")) scala3Options
      else if (scalaVersion.value.startsWith("2.13")) scala213Options
      else scala212Options
    })
  } else Seq.empty
}

lazy val coreSdk = project
  .in(file("sdk/core"))
  .enablePlugins(Publish)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .dependsOn(devTools)
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-jvm-core-sdk",
    // only packages that are to be released with scala 3 should have the _3 appended
    // i.e. java sdk package names should remain without suffix
    crossPaths := scalaVersion.value.startsWith("3."),
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    crossScalaVersions := Dependencies.CrossScalaVersions,
    // Generate javadocs by just including non generated Java sources
    Compile / doc / sources := {
      val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
      (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
    })

lazy val javaSdkProtobuf = project
  .in(file("sdk/java-sdk-protobuf"))
  .dependsOn(coreSdk)
  .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-java-sdk-protobuf",
    // only packages that are to be released with scala 3 should have the _3 appended
    // i.e. java sdk package names should remain without suffix
    crossPaths := scalaVersion.value.startsWith("3."),
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "runtimeImage" -> Kalix.RuntimeImage,
      "runtimeVersion" -> Kalix.RuntimeVersion,
      "protocolMajorVersion" -> Kalix.ProtocolVersionMajor,
      "protocolMinorVersion" -> Kalix.ProtocolVersionMinor,
      "scalaVersion" -> scalaVersion.value,
      "akkaVersion" -> Dependencies.AkkaVersion),
    buildInfoPackage := "kalix.javasdk",
    crossScalaVersions := Dependencies.CrossScalaVersions,
    // Generate javadocs by just including non generated Java sources
    Compile / doc / sources := {
      val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
      (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
    },
    // javadoc (I think java 9 onwards) refuses to compile javadocs if it can't compile the entire source path.
    // but since we have java files depending on Scala files, we need to include ourselves on the classpath.
    Compile / doc / dependencyClasspath := (Compile / fullClasspath).value,
    Compile / doc / javacOptions ++= Seq(
      "-Xdoclint:none",
      "-overview",
      ((Compile / javaSource).value / "overview.html").getAbsolutePath,
      "-notimestamp",
      "-doctitle",
      "Kalix Java Protobuf SDK",
      "-noqualifier",
      "java.lang"),
    Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server, AkkaGrpc.Client),
    Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala), // FIXME should be Java, but here be dragons
    // We need to generate the java files for things like entity_key.proto so that downstream libraries can use them
    // without needing to generate them themselves
    Compile / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "main",
    Test / javacOptions ++= Seq("-parameters"), // for Jackson
    Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
    Test / PB.protoSources ++= (Compile / PB.protoSources).value,
    Test / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "test")
  .settings(Dependencies.javaSdk)

lazy val javaSdkProtobufTestKit = project
  .in(file("sdk/java-sdk-protobuf-testkit"))
  .dependsOn(javaSdkProtobuf)
  .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .settings(commonCompilerSettings)
  .settings(
    name := "kalix-java-sdk-protobuf-testkit",
    // only packages that are to be released with scala 3 should have the _3 appended
    // i.e. java sdk package names should remain without suffix
    crossPaths := scalaVersion.value.startsWith("3."),
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "runtimeImage" -> Kalix.RuntimeImage,
      "runtimeVersion" -> Kalix.RuntimeVersion,
      "scalaVersion" -> scalaVersion.value),
    buildInfoPackage := "kalix.javasdk.testkit",
    crossScalaVersions := Dependencies.CrossScalaVersions,
    // Generate javadocs by just including non generated Java sources
    Compile / doc / sources := {
      val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
      (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
    },
    // javadoc (I think java 9 onwards) refuses to compile javadocs if it can't compile the entire source path.
    // but since we have java files depending on Scala files, we need to include ourselves on the classpath.
    Compile / doc / dependencyClasspath := (Compile / fullClasspath).value,
    Compile / doc / javacOptions ++= Seq(
      "-Xdoclint:none",
      "-overview",
      ((Compile / javaSource).value / "overview.html").getAbsolutePath,
      "-notimestamp",
      "-doctitle",
      "Kalix Java Protobuf SDK Testkit",
      "-noqualifier",
      "java.lang"))
  .settings(Dependencies.javaSdkTestKit)

lazy val scalaSdkProtobuf = project
  .in(file("sdk/scala-sdk-protobuf"))
  .dependsOn(javaSdkProtobuf)
  .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-scala-sdk-protobuf",
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    buildInfoObject := "ScalaSdkBuildInfo",
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "protocolMajorVersion" -> Kalix.ProtocolVersionMajor,
      "protocolMinorVersion" -> Kalix.ProtocolVersionMinor,
      "scalaVersion" -> scalaVersion.value,
      "akkaVersion" -> Dependencies.AkkaVersion),
    crossScalaVersions := Dependencies.CrossScalaVersions,
    buildInfoPackage := "kalix.scalasdk",
    Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
    Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
    Test / javacOptions += "-parameters", // for Jackson
    inTask(doc)(
      Seq(
        Compile / scalacOptions ++= scaladocOptions(
          "Kalix Scala Protobuf SDK",
          version.value,
          (ThisBuild / baseDirectory).value))))
  .settings(Dependencies.scalaSdk)

lazy val scalaSdkProtobufTestKit = project
  .in(file("sdk/scala-sdk-protobuf-testkit"))
  .dependsOn(scalaSdkProtobuf)
  .dependsOn(javaSdkProtobufTestKit)
  .enablePlugins(BuildInfoPlugin, Publish)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-scala-sdk-protobuf-testkit",
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "protocolMajorVersion" -> Kalix.ProtocolVersionMajor,
      "protocolMinorVersion" -> Kalix.ProtocolVersionMinor,
      "scalaVersion" -> scalaVersion.value),
    crossScalaVersions := Dependencies.CrossScalaVersions,
    buildInfoPackage := "kalix.scalasdk.testkit",
    inTask(doc)(
      Seq(
        Compile / scalacOptions ++= scaladocOptions(
          "Kalix Scala Protobuf SDK TestKit",
          version.value,
          (ThisBuild / baseDirectory).value))))
  .settings(Dependencies.scalaSdkTestKit)

def scaladocOptions(title: String, ver: String, base: File): List[String] = {
  val urlString = githubUrl(ver) + "/€{FILE_PATH_EXT}#L€{FILE_LINE}"
  List(
    "-implicits",
    "-groups",
    "-doc-source-url",
    urlString,
    "-sourcepath",
    base.getAbsolutePath,
    "-doc-title",
    title,
    "-doc-version",
    ver)
}

def githubUrl(v: String): String = {
  val branch = if (v.endsWith("SNAPSHOT")) "main" else "v" + v
  "https://github.com/lightbend/kalix-jvm-sdk/tree/" + branch
}

lazy val devTools = devToolsCommon(
  project
    .in(file("devtools"))
    .settings(
      name := "kalix-devtools",
      scalaVersion := Dependencies.ScalaVersion,
      crossScalaVersions := Dependencies.CrossScalaVersions))

/*
  This variant devTools compiles with Scala 2.12, but uses the same source files as the 2.13 version (above).
  This is needed because the devTools artifact is also used by the sbt and mvn plugins and therefore needs a 2.12 version.
  Note that crossbuilding is not an option, because when the built selects 2.13, it will try to build sbt-kalix with 2.13
 */
lazy val devToolsInternal =
  devToolsCommon(
    project
      .in(file("devtools"))
      .settings(
        name := "kalix-devtools-internal",
        scalaVersion := Dependencies.ScalaVersionForTooling,
        // to avoid overwriting the 2.13 version
        target := baseDirectory.value / "target-2.12"))

/*
 Common configuration to be applied to devTools modules (both 2.12 and 2.13)
 We need to have this 'split' module because compilation of sbt plugins don't play nice with cross-compiled modules.
 Instead, it's easier to have a separate module for each Scala version, but share the same source files.
 */
def devToolsCommon(project: Project): Project =
  project
    .enablePlugins(BuildInfoPlugin, Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    // TODO: need fix in KalixPlugin
    // .settings(disciplinedScalacSettings)
    .settings(
      Compile / javacOptions ++= Seq("--release", "11"),
      Compile / scalacOptions ++= Seq("-release", "11"),
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        "runtimeImage" -> Kalix.RuntimeImage,
        "runtimeVersion" -> Kalix.RuntimeVersion),
      buildInfoPackage := "kalix.devtools",
      // Generate javadocs by just including non generated Java sources
      Compile / doc / sources := {
        val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
        (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
      },
      // javadoc (I think java 9 onwards) refuses to compile javadocs if it can't compile the entire source path.
      // but since we have java files depending on Scala files, we need to include ourselves on the classpath.
      Compile / doc / dependencyClasspath := (Compile / fullClasspath).value,
      Compile / doc / javacOptions ++= Seq(
        "-Xdoclint:none",
        "-overview",
        ((Compile / javaSource).value / "overview.html").getAbsolutePath,
        "-notimestamp",
        "-doctitle",
        "Kalix Dev Tools",
        "-noqualifier",
        "java.lang"))
    .settings(Dependencies.devTools)

lazy val javaTck = project
  .in(file("tck/java-tck"))
  .dependsOn(javaSdkProtobuf, javaSdkProtobufTestKit)
  .enablePlugins(AkkaGrpcPlugin, PublicDockerImage, ReflectiveCodeGen)
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-tck-java-sdk",
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    crossScalaVersions := Dependencies.CrossScalaVersions,
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
    ReflectiveCodeGen.copyUnmanagedSources := true,
    Compile / mainClass := Some("kalix.javasdk.tck.JavaSdkTck"),
    dockerEnvVars += "HOST" -> "0.0.0.0",
    dockerExposedPorts += 8080)
  .settings(Dependencies.tck)

lazy val scalaTck = project
  .in(file("tck/scala-tck"))
  .dependsOn(scalaSdkProtobuf, scalaSdkProtobufTestKit)
  .enablePlugins(AkkaGrpcPlugin, PublicDockerImage, ReflectiveCodeGen)
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-tck-scala-sdk",
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    crossScalaVersions := Dependencies.CrossScalaVersions,
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
    libraryDependencies ++= Seq(Dependencies.kalixSdkProtocol % "protobuf-src"),
    ReflectiveCodeGen.copyUnmanagedSources := true,
    Compile / mainClass := Some("kalix.scalasdk.tck.ScalaSdkTck"),
    dockerEnvVars += "HOST" -> "0.0.0.0",
    dockerExposedPorts += 8080)
  .settings(Dependencies.tck)

lazy val codegenCore =
  project
    .in(file("codegen/core"))
    .enablePlugins(sbtprotoc.ProtocPlugin, Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    .settings(disciplinedScalacSettings)
    .settings(
      name := "kalix-codegen-core",
      testFrameworks += new TestFramework("munit.Framework"),
      Test / fork := false)
    .settings(Dependencies.codegenCore)
    .settings(
      Compile / javacOptions ++= Seq("--release", "11"),
      Compile / scalacOptions ++= Seq("-release", "11"),
      scalaVersion := Dependencies.ScalaVersionForTooling,
      crossScalaVersions := Seq(Dependencies.ScalaVersionForTooling),
      Compile / PB.targets := Seq(PB.gens.java -> (Compile / sourceManaged).value))

lazy val codegenJava =
  project
    .in(file("codegen/java-gen"))
    .configs(IntegrationTest)
    .dependsOn(codegenCore % "compile->compile;test->test")
    .enablePlugins(Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(
      Test / fork := false, // needed to pass -D properties to ExampleSuite
      // to provide access to protoc to tests
      Test / buildInfoPackage := "kalix.codegen.java",
      Test / buildInfoKeys := Seq(
        BuildInfoKey(PB.protocExecutable),
        BuildInfoKey(codegenCore / PB.externalIncludePath),
        BuildInfoKey(codegenCore / PB.externalSourcePath),
        BuildInfoKey(Test / resourceDirectory)))
    // only need BuildInfo in Test scope so some manual setup here
    .settings(BuildInfoPlugin.buildInfoScopedSettings(Test) ++ BuildInfoPlugin.buildInfoDefaultSettings)
    .settings(commonCompilerSettings)
    .settings(disciplinedScalacSettings)
    .settings(Defaults.itSettings)
    .settings(name := "kalix-codegen-java", testFrameworks += new TestFramework("munit.Framework"))
    .settings(Dependencies.codegenJava)
    .settings(
      Compile / javacOptions ++= Seq("--release", "11"),
      Compile / scalacOptions ++= Seq("-release", "11"),
      scalaVersion := Dependencies.ScalaVersionForTooling,
      crossScalaVersions := Seq(Dependencies.ScalaVersionForTooling))

lazy val codegenJavaCompilationTest = project
  .in(file("codegen/java-gen-compilation-tests"))
  .enablePlugins(ReflectiveCodeGen)
  .dependsOn(javaSdkProtobuf)
  // code generated by the codegen requires the testkit, junit4
  // Note: we don't use test scope since all code is generated in src_managed
  // and the goal is to verify if it compiles
  .dependsOn(javaSdkProtobufTestKit)
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(libraryDependencies ++= Seq(Dependencies.junit4))
  .settings(
    scalaVersion := Dependencies.ScalaVersion,
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
    (publish / skip) := true,
    name := "kalix-codegen-java-compilation-tests",
    Compile / PB.protoSources += baseDirectory.value / ".." / ".." / "sbt-plugin" / "src" / "sbt-test" / "sbt-kalix" / "compile-only" / "src" / "main" / "protobuf",
    ReflectiveCodeGen.copyUnmanagedSources := false)

lazy val codegenScala =
  project
    .in(file("codegen/scala-gen"))
    .enablePlugins(BuildInfoPlugin)
    .enablePlugins(Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(Dependencies.codegenScala)
    .settings(commonCompilerSettings)
    .settings(disciplinedScalacSettings)
    .settings(
      name := "kalix-codegen-scala",
      Compile / javacOptions ++= Seq("--release", "11"),
      Compile / scalacOptions ++= Seq("-release", "11"),
      scalaVersion := Dependencies.ScalaVersionForTooling,
      crossScalaVersions := Seq(Dependencies.ScalaVersionForTooling),
      Test / fork := false, // needed to pass -D properties to ExampleSuite
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        organization,
        version,
        scalaVersion,
        sbtVersion,
        "akkaVersion" -> Dependencies.AkkaVersion,
        "protocolVersion" -> Kalix.RuntimeVersion,
        BuildInfoKey(PB.protocExecutable),
        BuildInfoKey(codegenCore / PB.externalIncludePath),
        BuildInfoKey(codegenCore / PB.externalSourcePath),
        BuildInfoKey(Test / resourceDirectory)),
      buildInfoPackage := "kalix.codegen.scalasdk",
      testFrameworks += new TestFramework("munit.Framework"))
    .dependsOn(codegenCore % "compile->compile;test->test")

lazy val codegenScalaCompilationTest = project
  .in(file("codegen/scala-gen-compilation-tests"))
  .enablePlugins(ReflectiveCodeGen)
  .dependsOn(scalaSdkProtobuf)
  // code generated by the codegen requires the testkit, scalatest
  // Note: we don't use test scope since all code is generated in src_managed
  // and the goal is to verify if it compiles
  .dependsOn(scalaSdkProtobufTestKit)
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(libraryDependencies ++= Seq(Dependencies.kalixSdkProtocol % "protobuf-src"))
  .settings(
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
    (publish / skip) := true,
    name := "kalix-codegen-scala-compilation-tests",
    Compile / PB.protoSources += baseDirectory.value / ".." / ".." / "sbt-plugin" / "src" / "sbt-test" / "sbt-kalix" / "compile-only" / "src" / "main" / "protobuf",
    ReflectiveCodeGen.copyUnmanagedSources := false)

lazy val codegenJavaCompilationExampleSuite: CompositeProject =
  ExampleSuiteCompilationProject.compilationProject(AkkaGrpc.Java, "codegen/java-gen/src/test/resources/tests") {
    testProject =>
      testProject.dependsOn(javaSdkProtobuf % "compile", javaSdkProtobufTestKit % "test")
  }

lazy val codegenScalaCompilationExampleSuite: CompositeProject =
  ExampleSuiteCompilationProject.compilationProject(AkkaGrpc.Scala, "codegen/scala-gen/src/test/resources/tests") {
    testProject =>
      testProject
        .dependsOn(scalaSdkProtobuf % "compile", scalaSdkProtobufTestKit % "test")
        .settings(
          akkaGrpcCodeGeneratorSettings += "flat_package",
          libraryDependencies ++= Seq(Dependencies.kalixSdkProtocol % "protobuf-src"))
  }

lazy val sbtPlugin = Project(id = "sbt-kalix", base = file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .enablePlugins(Publish)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .settings(Dependencies.sbtPlugin)
  .settings(commonCompilerSettings)
  .settings(
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    scalaVersion := Dependencies.ScalaVersionForTooling,
    crossScalaVersions := Seq(Dependencies.ScalaVersionForTooling),
    publishSignedConfiguration := publishSignedConfiguration.value.withArtifacts(
      // avoid publishing the plugin jar twice
      publishSignedConfiguration.value.artifacts.filter(!_._1.name.contains("2.12_1.0"))),
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false)
  .dependsOn(codegenScala, devToolsInternal)

addCommandAlias("formatAll", "scalafmtAll; javafmtAll")
