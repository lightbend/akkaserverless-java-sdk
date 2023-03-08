# Kalix JVM SDKs


The Kalix JVM SDKs are used to develop Kalix services using Java or Scala. Two different development approaches are available as listed below: 
- Java SDK (code-first)
- Java Protobuf SDK (protocol-first)
- Scala Protobuf SDK (protocol-first)

If you’re just looking to get started, we advise you to start with the Java SDK which is the only one supporting a code-first development approach at present. For more information see the documentation for [implementing Kalix services in Java](https://docs.kalix.io/java/index.html).


## Java SDK

* User-facing packages
  * `kalix-spring-boot-starter`: The Java API to build services in Kalix with a code-first development approach using a Spring Boot Starter. See [Java SDK](https://docs.kalix.io/java/index.html).
  * `kalix-spring-boot-starter-test`: A library to implement integration tests for services with Junit 4 or 5, based on [TestContainers](https://www.testcontainers.org/). Also contains library parts of unit TestKit.
* [Samples](samples/): Small example services to illustrate Kalix features for this SDK are prefixed with `java-spring-`. The code also provides snippets for the documentation.
* Developer tooling
  * `maven-java` Maven tooling
    * `kalix-spring-boot-archetype` Maven archetype to create a project.

## Java/Scala Protobuf SDK

* User-facing packages
  * `kalix-java-sdk-protobuf` and `kalix-scala-sdk-protobuf`: The Java/Scala Protobuf API to build services in Kalix (backed by an implementation in Scala based on Akka gRPC). See [Java/Scala Protobuf SDK](https://docs.kalix.io/java-protobuf/index.html).

  * `kalix-java-sdk-testkit` and `kalix-scala-sdk-testkit`: A library to implement integration tests for services with Junit 4 or 5, based on [TestContainers](https://www.testcontainers.org/). Also contains library parts of unit TestKit.

* [Samples](samples/): Small example services to illustrate Kalix features are prefixed with `java-protobuf-` or `scala-protobuf-`. The code provides snippets for the documentation.

* Developer tooling
   * `codegen/core`, `codegen/java-gen`, `codegen/scala-gen` Tooling to generate code from Protobuf with annotations.
   * `maven-java` Maven tooling
     * `kalix-maven-plugin` Maven plugin to trigger code generation.
     * `kalix-maven-archetype-event-sourced-entity` Maven archetype to create a project with an Event-sourced Entity.
     * `kalix-maven-archetype-value-entity` Maven archetype to create a project with a Value Entity.
   * `sbt-plugin` sbt plugin for code generation.
   * `sbt new` (gitter8) templates:
     * [lightbend/kalix-value-entity.g8](https://github.com/lightbend/kalix-value-entity.g8) 

## Common parts
* `docs` The documentation feeding into [Java SDK](https://docs.kalix.io/java/index.html) and [Java/Scala Protobuf SDK](https://docs.kalix.io/java/index.html).

* `tck` The Technology Compatibility Kit which ensures the Java SDK adheres to the Kalix protocol.

# License

The Kalix JVM SDKs are Open Source and available under the [Apache 2 License](LICENSE).

