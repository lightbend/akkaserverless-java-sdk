package customer.view;

import customer.api.CustomerEntity;
import customer.api.CustomerEvent;
import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import org.springframework.web.bind.annotation.GetMapping;

@Table("customers_by_name")
public class CustomerByNameView extends View<CustomerView> {

  @GetMapping("/customer/by_name/{customer_name}")
  @Query("SELECT * FROM customers_by_name WHERE name = :customer_name")
  public CustomerView getCustomer(String name) {
    return null;
  }

  @Subscribe.EventSourcedEntity(CustomerEntity.class)
  public UpdateEffect<CustomerView> onEvent(CustomerEvent.CustomerCreated created) {
    return effects().updateState(new CustomerView(created.email(), created.name(), created.address()));
  }

  @Subscribe.EventSourcedEntity(CustomerEntity.class)
  public UpdateEffect<CustomerView> onEvent(CustomerEvent.NameChanged event) {
    return effects().updateState(viewState().withName(event.newName()));
  }

  @Subscribe.EventSourcedEntity(CustomerEntity.class)
  public UpdateEffect<CustomerView> onEvent(CustomerEvent.AddressChanged event) {
    return effects().updateState(viewState().withAddress(event.address()));
  }
}
