package customer.api;


import customer.Main;
import customer.view.CustomerView;
import kalix.springsdk.testkit.KalixIntegrationTestKitSupport;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Main.class)
public class CustomerIntegrationTest extends KalixIntegrationTestKitSupport {


  @Autowired
  private WebClient webClient;

  private Duration timeout = Duration.of(5, SECONDS);

  @Test
  public void create() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    ResponseEntity<String> response =
        webClient.post()
            .uri("/customer/" + id + "/create")
            .bodyValue(customer)
            .retrieve()
            .toEntity(String.class)
            .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    Assertions.assertEquals("Johanna", getCustomerById(id).name);
  }

  @Test
  public void changeName() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    ResponseEntity<String> resCreation =
        webClient.post()
            .uri("/customer/" + id + "/create")
            .body(Mono.just(customer), Customer.class)
            .retrieve()
            .toEntity(String.class)
            .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, resCreation.getStatusCode());

    ResponseEntity<String> resUpdate =
        webClient.post()
            .uri("/customer/" + id + "/changeName/" + "Katarina")
            .retrieve()
            .toEntity(String.class)
            .block(timeout);


    Assertions.assertEquals(HttpStatus.OK, resUpdate.getStatusCode());
    Assertions.assertEquals("Katarina", getCustomerById(id).name);
  }

  @Test
  public void changeAddress() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    ResponseEntity<String> resCreation =
        webClient.post()
            .uri("/customer/" + id + "/create")
            .body(Mono.just(customer), Customer.class)
            .retrieve()
            .toEntity(String.class)
            .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, resCreation.getStatusCode());

    Address address = new Address("Elm st. 5", "New Orleans");
    ResponseEntity<String> resUpdate =
        webClient.post()
            .uri("/customer/" + id + "/changeAddress")
            .body(Mono.just(address), Address.class)
            .retrieve()
            .toEntity(String.class)
            .block(timeout);


    Assertions.assertEquals(HttpStatus.OK, resUpdate.getStatusCode());
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address.street);
  }


  @Test
  public void findByName() throws Exception {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Foo", null);
    ResponseEntity<String> response =
        webClient.post()
            .uri("/customer/" + id + "/create")
            .bodyValue(customer)
            .retrieve()
            .toEntity(String.class)
            .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());

    // the view is eventually updated
    await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(() ->
                webClient.get()
                    .uri("/customer/by_name/Foo")
                    .retrieve()
                    .bodyToMono(CustomerView.class)
                    .block(timeout)
                    .name,
            new IsEqual("Foo")
        );
  }

  @Test
  public void findByEmail() throws Exception {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "bar@example.com", "Bar", null);
    ResponseEntity<String> response =
        webClient.post()
            .uri("/customer/" + id + "/create")
            .bodyValue(customer)
            .retrieve()
            .toEntity(String.class)
            .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());

    // the view is eventually updated
    await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(() ->
                webClient.get()
                    .uri("/customer/by_email/bar@example.com")
                    .retrieve()
                    .bodyToMono(CustomerView.class)
                    .block(timeout)
                    .name,
            new IsEqual("Bar")
        );
  }

  private Customer getCustomerById(String customerId) {
    return webClient
        .get()
        .uri("/customer/" + customerId)
        .retrieve()
        .bodyToMono(Customer.class)
        .block(timeout);
  }

}
