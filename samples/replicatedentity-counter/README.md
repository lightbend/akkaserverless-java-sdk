# Implementing a Counter as a Replicated Entity

## Building and running unit tests

To compile and test the code from the command line, use

```shell
mvn verify
```

## Running the integration tests

The integration test sources are in the `src/it/java` directory.

NOTE: As this is not part of the standard Maven setup, you might need to add it as test source directory in your IDE. (IntelliJ: right klick on the directory -> "Mark directory as" | "Test sources root")

Maven is configured to run tests from it when using the `it` profile.

```shell
mvn verify -Pit
```

The integration test uses Docker via [TestContainers](https://www.testcontainers.org/) to set up the Akka Serverless environment before calling the gRPC API.

## Running Locally

In order to run your application locally, you must run the Akka Serverless proxy. The included `docker compose` file contains the configuration required to run the proxy for a locally running application.
It also contains the configuration to start a local Google Pub/Sub emulator that the Akka Serverless proxy will connect to.
To start the proxy, run the following command from this directory:


```shell
docker compose up
```

> On Linux this requires Docker 20.10 or later (https://github.com/moby/moby/pull/40007),
> or for a `USER_FUNCTION_HOST` environment variable to be set manually.

```shell
docker compose -f docker-compose.yml -f docker-compose.linux.yml up
```

To start the application locally, the `exec-maven-plugin` is used. Use the following command:

```shell
mvn compile exec:java
```

For further details see [Running a service locally](https://developer.lightbend.com/docs/akka-serverless/developing/running-service-locally.html) in the documentation.

## Exercise the service

With both the proxy and your application running, any defined endpoints should be available at `http://localhost:9000`. In addition to the defined gRPC interface, each method has a corresponding HTTP endpoint. Unless configured otherwise (see [Transcoding HTTP](https://developer.lightbend.com/docs/akka-serverless/java/proto.html#_transcoding_http)), this endpoint accepts POST requests at the path `/[package].[entity name]/[method]`. For example, using `curl`:

```shell
curl -XPOST -H "Content-Type: application/json" localhost:9000/com.example.CounterService/GetCurrentCounter -d '{"counterId": "foo"}'
```

Or, given [`grpcurl`](https://github.com/fullstorydev/grpcurl):

```shell
grpcurl -plaintext -d '{"counterId": "foo"}' localhost:9000 com.example.CounterService/GetCurrentCounter 
```

## Deploying

To deploy your service, install the `akkasls` CLI as documented in
[Setting up a local development environment](https://developer.lightbend.com/docs/akka-serverless/getting-started/set-up-development-env.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://developer.lightbend.com/docs/akka-serverless/deploying/registries.html)
for more information on how to make your docker image available to Akka Serverless.

Finally, you can use the [Akka Serverless Console](https://console.akkaserverless.com)
to create an Akka Serverless project and then deploy your service into it either by using `mvn deploy`,
through the `akkasls` CLI or via the web interface. When using `mvn deploy`, Maven will also
conveniently package and publish your docker image prior to deployment.
