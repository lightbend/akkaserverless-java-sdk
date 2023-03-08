package customer.view;

// tag::class[]
import customer.api.Customer;
import customer.api.CustomerEntity;
import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import kalix.springsdk.annotations.ViewId;
import org.springframework.web.bind.annotation.GetMapping;

@ViewId("view_customers_by_name") // <1>
@Table("customers_by_name")  // <2>
@Subscribe.ValueEntity(CustomerEntity.class) // <3>
public class CustomerByNameView extends View<Customer> { // <4>

  @GetMapping("/customer/by_name/{customer_name}")   // <5>
  @Query("SELECT * FROM customers_by_name WHERE name = :customer_name") // <6>
  public Customer getCustomer(String name) {
    return null; // <7>
  }
}
// end::class[]
