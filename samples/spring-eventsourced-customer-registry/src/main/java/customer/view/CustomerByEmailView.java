package customer.view;

import customer.api.CustomerEntity;
import customer.api.CustomerEvent;
import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import org.springframework.web.bind.annotation.GetMapping;

@Table("customers_by_email")
public class CustomerByEmailView extends View<CustomerView> {

  @GetMapping("/customer/by_email/{email}")
  @Query("SELECT * FROM customers_by_email WHERE email = :email")
  public CustomerView getCustomer(String email) {
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
