package com.example;

import com.example.shoppingcart.Main;
import com.example.shoppingcart.domain.ShoppingCart;
import com.example.shoppingcart.domain.ShoppingCart.LineItem;
import kalix.springsdk.testkit.KalixIntegrationTestKitSupport;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;


/**
 * This is a skeleton for implementing integration tests for a Kalix application built with the Spring SDK.
 * <p>
 * This test will initiate a Kalix Proxy using testcontainers and therefore it's required to have Docker installed
 * on your machine. This test will also start your Spring Boot application.
 * <p>
 * Since this is an integration tests, it interacts with the application using a WebClient
 * (already configured and provided automatically through injection).
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Main.class)
public class IntegrationTest extends KalixIntegrationTestKitSupport {

  @Autowired
  private WebClient webClient;

  private Duration timeout = Duration.of(5, SECONDS);

  @Test
  public void createAndManageCart() {

    String cartId = "card-abc";

    ResponseEntity<String> created =
      webClient.post()
        .uri("/cart/" + cartId + "/create")
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, created.getStatusCode());

    var item1 = new LineItem("tv", "Super TV 55'", 1);

    ResponseEntity<String> itemOne =
      webClient.post()
        .uri("/cart/" + cartId + "/add")
        .bodyValue(item1)
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, itemOne.getStatusCode());

    var item2 = new LineItem("tv-table", "Table for TV", 1);
    ResponseEntity<String> itemTwo =
      webClient.post()
        .uri("/cart/" + cartId + "/add")
        .bodyValue(item2)
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    Assertions.assertEquals(HttpStatus.OK, itemTwo.getStatusCode());

    ShoppingCart cartInfo =
      webClient.get()
        .uri("/cart/" + cartId)
        .retrieve()
        .bodyToMono(ShoppingCart.class)
        .block(timeout);
    Assertions.assertEquals(2, cartInfo.items().size());


    // removing one of the items
    ResponseEntity<String> removingItemOne =
      webClient.post()
        .uri("/cart/" + cartId + "/items/" + item1.productId() + "/remove")
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    // confirming only one product remains
    ShoppingCart cartUpdated =
      webClient.get()
        .uri("/cart/" + cartId)
        .retrieve()
        .bodyToMono(ShoppingCart.class)
        .block(timeout);
    Assertions.assertEquals(1, cartUpdated.items().size());
    Assertions.assertEquals(item2, cartUpdated.items().get(0));
  }
}