package customer.api;

import customer.domain.Address;
import customer.domain.Customer;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;
import org.springframework.web.bind.annotation.*;

import static customer.domain.CustomerEvent.*;

@EntityKey("id")
@EntityType("customer")
@RequestMapping("/customer/{id}")
public class CustomerEntity extends EventSourcedEntity<Customer> {

  @GetMapping
  public Effect<Customer> getCustomer() {
    return effects().reply(currentState());
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody Customer customer) {
    return effects()
        .emitEvent(new CustomerCreated(customer.email(), customer.name(), customer.address()))
        .thenReply(__ -> "OK");
  }

  @EventHandler
  public Customer onEvent(CustomerCreated created) {
    return new Customer(created.email(), created.name(), created.address());
  }


  @PostMapping("/changeName/{newName}")
  public Effect<String> changeName(@PathVariable String newName) {

    return effects()
        .emitEvent(new NameChanged(newName))
        .thenReply(__ -> "OK");
  }

  @EventHandler
  public Customer onEvent(NameChanged nameChanged) {
    return currentState().withName(nameChanged.newName());
  }


  @PostMapping("/changeAddress")
  public Effect<String> changeAddress(@RequestBody Address newAddress) {
    return effects()
        .emitEvent(new AddressChanged(newAddress))
        .thenReply(__ -> "OK");
  }

  @EventHandler
  public Customer onEvents(AddressChanged addressChanged) {
    return currentState().withAddress(addressChanged.address());
  }
}
