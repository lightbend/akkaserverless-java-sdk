import Dependencies.AkkaServerless

lazy val `akkaserverless-java-sdk` = project
  .in(file("."))
  .aggregate(
    sdkCore,
    sdkJava,
    sdkScala,
    sdkJavaTestKit,
    sdkScalaTestKit,
    tck,
    codegenCore,
    codegenJava,
    codegenJavaCompilationTest,
    codegenScala,
    sbtPlugin)

def common: Seq[Setting[_]] =
  Seq(
    Compile / javacOptions ++= Seq("-encoding", "UTF-8", "--release", "11"),
    Compile / scalacOptions ++= Seq("-encoding", "UTF-8", "-release", "11"))

lazy val sdkCore = project
  .in(file("sdk/core"))
  .enablePlugins(PublishSonatype)
  .settings(common)
  .settings(
    name := "akkaserverless-jvm-sdk",
    crossPaths := false,
    // Generate javadocs by just including non generated Java sources
    Compile / doc / sources := {
      val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
      (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
    })
  .settings(Dependencies.sdkCore)

lazy val sdkJava = project
  .in(file("sdk/java-sdk"))
  .dependsOn(sdkCore)
  .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, PublishSonatype)
  .settings(common)
  .settings(
    name := "akkaserverless-java-sdk",
    crossPaths := false,
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "protocolMajorVersion" -> AkkaServerless.ProtocolVersionMajor,
      "protocolMinorVersion" -> AkkaServerless.ProtocolVersionMinor,
      "scalaVersion" -> scalaVersion.value),
    buildInfoPackage := "com.akkaserverless.javasdk",
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
      "--no-module-directories",
      "-notimestamp",
      "-doctitle",
      "Akka Serverless Java SDK",
      "-noqualifier",
      "java.lang"),
    Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
    Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala), // FIXME should be Java, but here be dragons
    // We need to generate the java files for things like entity_key.proto so that downstream libraries can use them
    // without needing to generate them themselves
    Compile / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "main",
    Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
    Test / PB.protoSources ++= (Compile / PB.protoSources).value,
    Test / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "test")
  .settings(Dependencies.sdkJava)

lazy val sdkScala = project
  .in(file("sdk/scala-sdk"))
  .dependsOn(sdkJava)
  .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, PublishSonatype)
  .settings(common)
  .settings(
    name := "akkaserverless-scala-sdk",
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "protocolMajorVersion" -> AkkaServerless.ProtocolVersionMajor,
      "protocolMinorVersion" -> AkkaServerless.ProtocolVersionMinor,
      "scalaVersion" -> scalaVersion.value),
    buildInfoPackage := "com.akkaserverless.scalasdk",
    Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
    Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
    Test / javacOptions += "-parameters", // for Jackson
    inTask(doc)(
      Seq(
        Compile / scalacOptions ++= scaladocOptions(
          "Akka Serverless Scala SDK",
          version.value,
          (ThisBuild / baseDirectory).value))))
  .settings(Dependencies.sdkScala)

lazy val sdkScalaTestKit = project
  .in(file("sdk/scala-sdk-testkit"))
  .dependsOn(sdkScala)
  .dependsOn(sdkJavaTestKit)
  .enablePlugins(BuildInfoPlugin, PublishSonatype)
  .settings(common)
  .settings(
    name := "akkaserverless-scala-sdk-testkit",
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "protocolMajorVersion" -> AkkaServerless.ProtocolVersionMajor,
      "protocolMinorVersion" -> AkkaServerless.ProtocolVersionMinor,
      "scalaVersion" -> scalaVersion.value),
    buildInfoPackage := "com.akkaserverless.scalasdk.testkit",
    inTask(doc)(
      Seq(
        Compile / scalacOptions ++= scaladocOptions(
          "Akka Serverless Scala SDK TestKit",
          version.value,
          (ThisBuild / baseDirectory).value))))
  .settings(Dependencies.sdkScalaTestKit)

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
  "https://github.com/lightbend/akkaserverless-java-sdk/tree/" + branch
}

lazy val sdkJavaTestKit = project
  .in(file("sdk/java-sdk-testkit"))
  .dependsOn(sdkJava)
  .enablePlugins(BuildInfoPlugin, PublishSonatype)
  .settings(common)
  .settings(
    name := "akkaserverless-java-sdk-testkit",
    crossPaths := false,
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "proxyImage" -> "gcr.io/akkaserverless-public/akkaserverless-proxy",
      "proxyVersion" -> AkkaServerless.FrameworkVersion,
      "scalaVersion" -> scalaVersion.value),
    buildInfoPackage := "com.akkaserverless.javasdk.testkit",
    // Produce javadoc by restricting to Java sources only -- no genjavadoc setup currently
    Compile / doc / sources := (Compile / doc / sources).value.filterNot(_.name.endsWith(".scala")))
  .settings(Dependencies.sdkJavaTestKit)

//FIXME add scalasdk as package to tck, tck will test both java and scala sdk
lazy val tck = project
  .in(file("tck"))
  .dependsOn(sdkJava, sdkJavaTestKit % Test)
  .enablePlugins(AkkaGrpcPlugin, PublicDockerImage)
  .settings(common)
  .settings(
    name := "akkaserverless-tck-java-sdk",
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
    Compile / mainClass := Some("com.akkaserverless.javasdk.tck.JavaSdkTck"),
    dockerEnvVars += "HOST" -> "0.0.0.0",
    dockerExposedPorts += 8080)
  .settings(Dependencies.tck)

lazy val codegenCore =
  project
    .in(file("codegen/core"))
    .enablePlugins(AkkaGrpcPlugin, PublishSonatype)
    .settings(common)
    .settings(
      name := "akkaserverless-codegen-core",
      testFrameworks += new TestFramework("munit.Framework"),
      Test / fork := false)
    .settings(Dependencies.codegenCore)
    .settings(Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java))
    .settings(
      crossScalaVersions := Dependencies.ScalaVersionForCodegen,
      scalaVersion := Dependencies.ScalaVersionForCodegen.head)

lazy val codegenJava =
  project
    .in(file("codegen/java-gen"))
    .configs(IntegrationTest)
    .dependsOn(codegenCore % "compile->compile;test->test")
    .enablePlugins(PublishSonatype)
    .settings(common)
    .settings(Defaults.itSettings)
    .settings(name := "akkaserverless-codegen-java", testFrameworks += new TestFramework("munit.Framework"))
    .settings(Dependencies.codegenJava)
    .settings(
      crossScalaVersions := Dependencies.ScalaVersionForCodegen,
      scalaVersion := Dependencies.ScalaVersionForCodegen.head)

lazy val codegenJavaCompilationTest = project
  .in(file("codegen/java-gen-compilation-tests"))
  .enablePlugins(ReflectiveCodeGen)
  .dependsOn(sdkJava)
  // code generated by the codegen requires the testkit, junit4
  // Note: we don't use test scope since all code is generated in src_managed
  // and the goal is to verify if it compiles
  .dependsOn(sdkJavaTestKit)
  .settings(common)
  .settings(libraryDependencies ++= Seq(Dependencies.junit4))
  .settings(
    (publish / skip) := true,
    name := "akkaserverless-codegen-java-compilation-tests",
    Compile / PB.protoSources += baseDirectory.value / ".." / ".." / "sbt-plugin" / "src" / "sbt-test" / "sbt-akkaserverless" / "compile-only" / "src" / "main" / "protobuf")

lazy val javaValueentityCustomerRegistry = project
  .in(file("samples/java-valueentity-customer-registry"))
  .dependsOn(sdkJava)
  .enablePlugins(AkkaGrpcPlugin, IntegrationTests, LocalDockerImage)
  .settings(common)
  .settings(
    name := "java-valueentity-customer-registry",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % Dependencies.LogbackVersion,
      "ch.qos.logback.contrib" % "logback-json-classic" % Dependencies.LogbackContribVersion,
      "ch.qos.logback.contrib" % "logback-jackson" % Dependencies.LogbackContribVersion,
      "org.junit.jupiter" % "junit-jupiter" % Dependencies.JUnitJupiterVersion % IntegrationTest,
      "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % IntegrationTest),
    Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
    testOptions += Tests.Argument(jupiterTestFramework, "-q", "-v"),
    inConfig(IntegrationTest)(JupiterPlugin.scopedSettings),
    IntegrationTest / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
    IntegrationTest / PB.protoSources ++= (Compile / PB.protoSources).value)

lazy val javaEventsourcedCustomerRegistry = project
  .in(file("samples/java-eventsourced-customer-registry"))
  .dependsOn(sdkJava)
  .enablePlugins(AkkaGrpcPlugin, IntegrationTests, LocalDockerImage)
  .settings(common)
  .settings(
    name := "java-eventsourced-customer-registry",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % Dependencies.LogbackVersion,
      "ch.qos.logback.contrib" % "logback-json-classic" % Dependencies.LogbackContribVersion,
      "ch.qos.logback.contrib" % "logback-jackson" % Dependencies.LogbackContribVersion,
      "org.junit.jupiter" % "junit-jupiter" % Dependencies.JUnitJupiterVersion % IntegrationTest,
      "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % IntegrationTest),
    Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
    testOptions += Tests.Argument(jupiterTestFramework, "-q", "-v"),
    inConfig(IntegrationTest)(JupiterPlugin.scopedSettings),
    IntegrationTest / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
    IntegrationTest / PB.protoSources ++= (Compile / PB.protoSources).value)

lazy val protobufDescriptorSetOut = settingKey[File]("The file to write the protobuf descriptor set to")

lazy val attachProtobufDescriptorSets = Seq(
  protobufDescriptorSetOut := (Compile / resourceManaged).value / "protobuf" / "descriptor-sets" / "user-function.desc",
  Compile / PB.generate := (Compile / PB.generate)
    .dependsOn(Def.task {
      protobufDescriptorSetOut.value.getParentFile.mkdirs()
    })
    .value,
  Compile / PB.targets := Seq(PB.gens.java -> (Compile / sourceManaged).value),
  Compile / PB.protocOptions ++= Seq(
    "--descriptor_set_out",
    protobufDescriptorSetOut.value.getAbsolutePath,
    "--include_source_info"),
  Compile / managedResources += protobufDescriptorSetOut.value,
  Compile / unmanagedResourceDirectories ++= (Compile / PB.protoSources).value)

lazy val codegenScala =
  project
    .in(file("codegen/scala-gen"))
    .enablePlugins(BuildInfoPlugin)
    .enablePlugins(PublishSonatype)
    .settings(Dependencies.codegenScala)
    .settings(common)
    .settings(
      name := "akkaserverless-codegen-scala",
      scalaVersion := Dependencies.ScalaVersionForSbtPlugin,
      buildInfoKeys := Seq[BuildInfoKey](name, organization, version, scalaVersion, sbtVersion),
      buildInfoPackage := "com.akkaserverless.codegen.scalasdk",
      testFrameworks += new TestFramework("munit.Framework"))
    .dependsOn(codegenCore % "compile->compile;test->test")

lazy val sbtPlugin = Project(id = "sbt-akkaserverless", base = file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .enablePlugins(PublishSonatype)
  .settings(Dependencies.sbtPlugin)
  .settings(common)
  .settings(
    scalaVersion := Dependencies.ScalaVersionForSbtPlugin,
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false)
  .dependsOn(codegenScala)
