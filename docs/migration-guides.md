# Migration Guides

## Migrating from 1.1.x to 1.2.x

### Java SDK

> **From 1.2.x onwards, the Java SDK is now called Java Protobuf SDK.**

Accordingly, the package dependencies were updated accordingly:
- from `kalix-java-sdk`to `kalix-java-sdk-protobuf`
- from `kalix-java-sdk-testkit` to `kalix-java-sdk-protobuf-testkit`.

Thus, in your `pom.xml`, instead of:

```xml
    <dependency>
      <groupId>io.kalix</groupId>
      <artifactId>kalix-java-sdk</artifactId>
      <version>${kalix-sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>io.kalix</groupId>
      <artifactId>kalix-java-sdk-testkit</artifactId>
      <version>${kalix-sdk.version}</version>
      <scope>test</scope>
    </dependency>
```

Use:
```xml
    <dependency>
      <groupId>io.kalix</groupId>
      <artifactId>kalix-java-sdk-protobuf</artifactId>
      <version>${kalix-sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>io.kalix</groupId>
      <artifactId>kalix-java-sdk-protobuf-testkit</artifactId>
      <version>${kalix-sdk.version}</version>
      <scope>test</scope>
    </dependency>
```

That's it!

### Scala SDK

>**From 1.2.x onwards, the Scala SDK is now called Scala Protobuf SDK.**

No changes required. The upgrade is a simple version bump here.
