# Fibonacci Action Service

This project is based on the Akka Serverless Maven archetype.

```shell
mvn archetype:generate \
  -DarchetypeGroupId=com.akkaserverless \
  -DarchetypeArtifactId=akkaserverless-maven-archetype \
  -DarchetypeVersion=LATEST
```

See the [Kickstart a Maven project](https://developer.lightbend.com/docs/akka-serverless/java/kickstart.html) in the documentation for details.

## Building and running unit tests

To compile and test the code from the command line, use

```shell
mvn verify
```

## Running Locally

In order to run your application locally, you must run the Akka Serverless proxy. The included `docker compose` file contains the configuration required to run the proxy for a locally running application.
It also contains the configuration to start a local Google Pub/Sub emulator that the Akka Serverless proxy will connect to.
To start the proxy, run the following command from this directory:

```
docker-compose up
```

> On Linux this requires Docker 20.10 or later (https://github.com/moby/moby/pull/40007),
> or for a `USER_FUNCTION_HOST` environment variable to be set manually.

To start the application locally, the `exec-maven-plugin` is used. Use the following command:

```
mvn compile exec:exec
```

For further details see [Running a service locally](https://developer.lightbend.com/docs/akka-serverless/developing/running-service-locally.html) in the documentation.

## Exercise the service

With both the proxy and your application running, any defined endpoints should be available at `http://localhost:9000`. In addition to the defined gRPC interface, each method has a corresponding HTTP endpoint. Unless configured otherwise (see [Transcoding HTTP](https://docs.lbcs.dev/js-services/proto.html#_transcoding_http)), this endpoint accepts POST requests at the path `/[package].[entity name]/[method]`. For example, using `curl`:

```shell
curl -XPOST -H "Content-Type: application/json" localhost:9000/fibonacci.Fibonacci/NextNumber -d '{"value": 5 }'
```

Or, given [`grpcurl`](https://github.com/fullstorydev/grpcurl):

```shell
grpcurl -plaintext -d '{"value": 5 }' localhost:9000 fibonacci.Fibonacci/NextNumber
```

## Deploying

To deploy your service, install the `akkasls` CLI as documented in
[Setting up a local development environment](https://developer.lightbend.com/docs/akka-serverless/setting-up/)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://developer.lightbend.com/docs/akka-serverless/projects/container-registries.html)
for more information on how to make your docker image available to Akka Serverless.

Finally, you can use the [Akka Serverless Console](https://console.akkaserverless.com)
to create an Akka Serverless project and then deploy your service into it either by using `mvn deploy`,
through the `akkasls` CLI or via the web interface. When using `mvn deploy`, Maven will also
conveniently package and publish your docker image prior to deployment.
