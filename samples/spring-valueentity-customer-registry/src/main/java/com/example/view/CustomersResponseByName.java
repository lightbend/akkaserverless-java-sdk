package com.example.view;

import com.example.api.CustomerEntity;
import com.example.api.CustomersResponse;
import com.example.domain.Customer;
import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import kalix.springsdk.annotations.ViewId;
import org.springframework.web.bind.annotation.GetMapping;

@ViewId("view_response_customers_by_name")
@Table("customers_by_name")
// tag::class[]
@Subscribe.ValueEntity(CustomerEntity.class)
public class CustomersResponseByName extends View<Customer> { // <1>

  @GetMapping("/wrapped/by_name/{customerName}")   // <2>
  @Query("""
    SELECT * AS customers
      FROM customers_by_name
      WHERE name = :customerName
    """) // <3>
  public CustomersResponse getCustomers() { // <4>
    return null;
  }
}
// end::class[]
