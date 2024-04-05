resolvers += "Akka library repository".at("https://repo.akka.io/maven")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
// even updated `akka-grpc.version` in pom.xml files
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.4.1")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.7.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.7.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")
addSbtPlugin("net.aichler" % "sbt-jupiter-interface" % "0.11.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.3")
