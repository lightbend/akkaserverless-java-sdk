# Quickstart project: Customer Registry with Views

## Designing

To understand the Kalix concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/services/development-process.html) in the documentation.


## Developing

This project demonstrates the use of Event Sourced Entity and View components.
To understand more about these components, see [Developing services](https://docs.kalix.io/services/)
and in particular the [Java section](https://docs.kalix.io/java/)


## Building

Use Maven to build your project:

```shell
mvn compile
```


## Running Locally

To run the example locally, you must run the Kalix proxy. The included `docker-compose` file contains the configuration required to run the proxy for a locally running application.
It also contains the configuration to start a local Google Pub/Sub emulator that the Kalix proxy will connect to.
To start the proxy, run the following command from this directory:

```shell
docker-compose up
```

To start the application locally, the `exec-maven-plugin` is used. Use the following command:

```shell
mvn spring-boot:run
```

With both the proxy and your application running, any defined endpoints should be available at `http://localhost:9000`. 


* Create a customer with:
  ```shell
  curl localhost:9000/customer/one/create \
    --header "Content-Type: application/json" \
    -XPOST \
    --data '{"customerId":"one","email":"test@example.com","name":"Test Testsson","address":{"street":"Teststreet 25","city":"Testcity"}}'
  ```
* Retrieve the customer:
  ```shell
  curl localhost:9000/customer/one
  ```
* Query by email:
  ```shell
  curl localhost:9000/customer/by_email/test%40example.com
  ```
* Query by name:
  ```shell
  curl localhost:9000/customer/by_name/Test%20Testsson
  ```
* Change name:
  ```shell
  curl localhost:9000/customer/one/changeName/Jan%20Banan -XPOST
  ```
* Change address:
  ```shell
  curl localhost:9000/customer/one/changeAddress \
    --header "Content-Type: application/json" \
    -XPOST \
    --data '{"street":"Newstreet 25","city":"Newcity"}'
  ```

## Deploying

To deploy your service, install the `kalix` CLI as documented in
[Setting up a local development environment](https://docs.kalix.io/setting-up/)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Kalix.

Finally, you can use the [Kalix Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy` which
will also conveniently package and publish your docker image prior to deployment, or by first packaging and
publishing the docker image through `mvn clean package docker:push -DskipTests` and then deploying the image
through the `kalix` CLI.
