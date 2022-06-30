import sbt._
import sbt.Keys._

object Dependencies {
  object Kalix {
    val ProtocolVersionMajor = 1
    val ProtocolVersionMinor = 0
    val ProxyVersion = System.getProperty("kalix-proxy.version", "1.0.11")
  }

  // changing the Scala version of the Java SDK affects end users
  val ScalaVersion = "2.13.8"
  val ScalaVersionForSbtPlugin = "2.12.15"
  val ScalaVersionForCodegen = Seq(ScalaVersionForSbtPlugin)

  val ProtobufVersion = akka.grpc.gen.BuildInfo.googleProtobufVersion

  val AkkaVersion = "2.6.19"
  val AkkaHttpVersion = "10.2.9" // Note: should at least the Akka HTTP version required by Akka gRPC
  val ScalaTestVersion = "3.2.12"
  val JacksonVersion = "2.13.2"
  val JacksonDatabindVersion = "2.13.2.2"
  val DockerBaseImageVersion = "adoptopenjdk/openjdk11:debianslim-jre"
  val LogbackVersion = "1.2.11"
  val LogbackContribVersion = "0.1.5"
  val TestContainersVersion = "1.17.1"
  val JUnitVersion = "4.13.2"
  val JUnitInterfaceVersion = "0.11"
  val JUnitJupiterVersion = "5.7.1"

  val CommonsIoVersion = "2.8.0"
  val MunitVersion = "0.7.29"
  val ScoptVersions = "4.0.0"

  val kalixProxyProtocol = "io.kalix" % "kalix-proxy-protocol" % Kalix.ProxyVersion
  val kalixSdkProtocol = "io.kalix" % "kalix-sdk-protocol" % Kalix.ProxyVersion
  val kalixTckProtocol = "io.kalix" % "kalix-tck-protocol" % Kalix.ProxyVersion

  val commonsIo = "commons-io" % "commons-io" % CommonsIoVersion
  val logback = "ch.qos.logback" % "logback-classic" % LogbackVersion
  val logbackJson = "ch.qos.logback.contrib" % "logback-json-classic" % LogbackContribVersion
  val logbackJackson = "ch.qos.logback.contrib" % "logback-jackson" % LogbackContribVersion

  val protobufJava = "com.google.protobuf" % "protobuf-java" % ProtobufVersion
  val protobufJavaUtil = "com.google.protobuf" % "protobuf-java-util" % ProtobufVersion

  val scopt = "com.github.scopt" %% "scopt" % ScoptVersions
  val jacksonCore = "com.fasterxml.jackson.core" % "jackson-core" % JacksonVersion
  val jacksonAnnotations = "com.fasterxml.jackson.core" % "jackson-annotations" % JacksonVersion
  val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % JacksonDatabindVersion
  val jacksonJdk8 = "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % JacksonVersion
  val jacksonJsr310 = "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % JacksonVersion
  val jacksonParameterNames = "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion
  val jacksonScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % JacksonVersion

  val scalaTest = "org.scalatest" %% "scalatest" % ScalaTestVersion
  val munit = "org.scalameta" %% "munit" % MunitVersion
  val munitScalaCheck = "org.scalameta" %% "munit-scalacheck" % MunitVersion
  val testContainers = "org.testcontainers" % "testcontainers" % TestContainersVersion
  val junit4 = "junit" % "junit" % JUnitVersion
  val junit5 = "org.junit.jupiter" % "junit-jupiter" % JUnitJupiterVersion

  val scalapbCompilerPlugin = "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
  val sbtProtoc = "com.thesamet" % "sbt-protoc" % "1.0.0"

  val akkaGrpc = "com.lightbend.akka.grpc" % "sbt-akka-grpc" % akka.grpc.gen.BuildInfo.version

  private val deps = libraryDependencies

  private val coreDeps = Seq(
    akkaDependency("akka-stream"),
    akkaDependency("akka-slf4j"),
    akkaDependency("akka-discovery"),
    akkaHttpDependency("akka-http"),
    akkaHttpDependency("akka-http-core"),
    akkaHttpDependency("akka-http-spray-json"),
    akkaHttpDependency("akka-http2-support"),
    akkaHttpDependency("akka-parsing"),
    protobufJavaUtil,
    kalixProxyProtocol % "protobuf-src",
    kalixSdkProtocol % "compile;protobuf-src",
    akkaDependency("akka-testkit") % Test,
    akkaDependency("akka-actor-testkit-typed") % Test,
    akkaDependency("akka-stream-testkit") % Test,
    akkaHttpDependency("akka-http-testkit") % Test,
    scalaTest % Test,
    logback,
    logbackJson,
    logbackJackson,
    jacksonCore,
    jacksonAnnotations,
    jacksonDatabind,
    jacksonJdk8,
    jacksonJsr310,
    jacksonParameterNames)

  val sdkCore = deps ++= coreDeps

  // FIXME
  val sdkJava = sdkCore

  val sdkJavaTestKit = deps ++= Seq(
    testContainers,
    // Override for security vulnerabilities. Can be removed once testcontainers is updated:
    // https://github.com/testcontainers/testcontainers-java/issues/4308
    "org.apache.commons" % "commons-compress" % "1.21",
    junit4 % Provided,
    junit5 % Provided,
    scalaTest % Test)

  // FIXME
  val sdkScala = deps ++= coreDeps ++ Seq(jacksonScala)

  val sdkScalaTestKit = deps ++= Seq(testContainers, logback % "test;provided", scalaTest % Test)

  val tck = deps ++= Seq(
    // FIXME: For now TCK protos have been copied and adapted into this project.
    //        Running the TCK is still meaningful as it runs the TCK check against the defined framework version.
    //        Eventually, the final form of protos from should be backported to the framework.
    //        See https://github.com/lightbend/kalix-jvm-sdk/issues/605
    //  kalixTckProtocol % "protobuf-src",
    //  "io.kalix" % "kalix-tck-protocol" % Kalix.ProxyVersion % "protobuf-src",
    "ch.qos.logback" % "logback-classic" % LogbackVersion)

  val codegenCore = deps ++= Seq(
    protobufJava,
    kalixSdkProtocol % "compile;protobuf-src",
    logback % Test,
    munit % Test,
    munitScalaCheck % Test)

  val codegenJava = deps ++= Seq(commonsIo, logback % Test, munit % Test, munitScalaCheck % Test)

  val codegenScala = deps ++= Seq(scalapbCompilerPlugin, munit % Test)

  val sbtPlugin = Seq(
    // we depend on it in the settings of the plugin since we set keys of the sbt-protoc plugin
    addSbtPlugin(sbtProtoc),
    addSbtPlugin(akkaGrpc))

  lazy val excludeTheseDependencies: Seq[ExclusionRule] = Seq(
    // exclusion rules can be added here
  )

  def akkaDependency(name: String, excludeThese: ExclusionRule*) =
    ("com.typesafe.akka" %% name % AkkaVersion).excludeAll((excludeTheseDependencies ++ excludeThese): _*)

  def akkaHttpDependency(name: String, excludeThese: ExclusionRule*) =
    ("com.typesafe.akka" %% name % AkkaHttpVersion).excludeAll((excludeTheseDependencies ++ excludeThese): _*)

}
