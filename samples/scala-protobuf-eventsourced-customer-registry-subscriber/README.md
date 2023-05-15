# Event Sourced Customer Registry Subscriber Sample

## Designing

To understand the Kalix concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/developing/development-process-proto.html) in the documentation.


## Developing

This project demonstrates consumption of a Service to Service eventing publisher. It consumes events produced and published
by a separate service, implemented in the `scala-protobuf-eventsourced-customer-registry` sample.

## Running Locally

First start the `scala-protobuf-eventsourced-customer-registry` service and proxy. It will run with the default service and proxy ports (`8080` and `9000`).

To start the application locally, use the following command:

```
sbt runAll
```

### Create a customer

```shell
grpcurl --plaintext -d '{"customer_id": "wip", "email": "wip@example.com", "name": "Very Important", "address": 
{"street": "Road 1", "city": "The Capital"}}' localhost:9001  customer.action.CustomerAction/Create
```

This call is made on the subscriber service and will be forwarded to the 
`scala-protobuf-eventsourced-customer-registry` service.

### Run a view query from this project

```shell
grpcurl --plaintext localhost:9001 customer.view.AllCustomersView/GetCustomers
```

The subscriber service will receive updates from customer-registry via service-to-service stream and update the view.

### Change name

```shell
grpcurl --plaintext -d '{"customer_id": "wip", "new_name": "Most Important"}' localhost:9000 customer.api.CustomerService/ChangeName
```

This call is performed on the customer-registry directly.

### Check the view again

```shell
grpcurl --plaintext localhost:9001 customer.view.AllCustomersView/GetCustomers
```
