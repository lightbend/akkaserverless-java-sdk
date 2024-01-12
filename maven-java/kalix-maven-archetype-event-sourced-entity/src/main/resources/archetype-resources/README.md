# ${artifactId}

#[[
## Designing
]]#
While designing your service it is useful to read [designing services](https://docs.kalix.io/developing/development-process-proto.html)

#[[
## Developing
]]#
This project has a bare-bones skeleton service ready to go, but in order to adapt and
extend it, it may be useful to read up on [developing services](https://docs.kalix.io/services/)
and in particular the [Java Protobuf SDK section](https://docs.kalix.io/java-protobuf/index.html)

#[[
## Building
]]#
You can use Maven to build your project, which will also take care of
generating code based on the `.proto` definitions:

```shell
mvn compile
```

#[[
## Running Locally
]]#

When running a Kalix service locally, we need to have its companion Kalix Runtime running alongside it.

To start your service locally, run:

```shell
mvn kalix:runAll
```

This command will start your Kalix service and a companion Kalix Runtime as configured in [docker-compose.yml](./docker-compose.yml) file.

> Note: if you're looking to use Google Pub/Sub, see comments inside [docker-compose.yml](./docker-compose.yml)
> on how to enable a Google Pub/Sub emulator that Kalix Runtime will connect to.

With both the Kalix Runtime and your service running, any defined endpoints should be available at `http://localhost:9000`. In addition to the defined gRPC interface, each method has a corresponding HTTP endpoint. Unless configured otherwise (see [Transcoding HTTP](https://docs.kalix.io/java-protobuf/writing-grpc-descriptors-protobuf.html#_transcoding_http)), this endpoint accepts POST requests at the path `/[package].[entity name]/[method]`. For example, using `curl`:

```shell
> curl -XPOST -H "Content-Type: application/json" localhost:9000/${package}.MyServiceEntity/GetValue -d '{"entityId": "foo"}'
The command handler for `GetValue` is not implemented, yet
```

For example, using [`grpcurl`](https://github.com/fullstorydev/grpcurl):

```shell
> grpcurl -plaintext -d '{"entityId": "foo"}' localhost:9000 ${package}.MyServiceEntity/GetValue
ERROR:
  Code: Unknown
  Message: The command handler for `GetValue` is not implemented, yet
```

> Note: The failure is to be expected if you have not yet provided an implementation of `GetValue` in
> your entity.

#[[
## Deploying
]]#
To deploy your service, install the `kalix` CLI as documented in
[Instal Kalix](https://docs.kalix.io/kalix/install-kalix.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Kalix.

Finally, you use the `kalix` CLI to create a project as described in [Create a new Project](https://docs.kalix.io/projects/create-project.html). Once you have a project you can deploy your service into the project either
by using `mvn deploy kalix:deploy` which will package, publish your docker image, and deploy your service to Kalix,
or by first packaging and publishing the docker image through `mvn deploy` and
then [deploying the image through the `kalix` CLI](https://docs.kalix.io/services/deploy-service.html#_deploy).

