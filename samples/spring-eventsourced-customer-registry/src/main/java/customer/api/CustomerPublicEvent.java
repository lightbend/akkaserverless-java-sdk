package customer.api;

import kalix.springsdk.annotations.TypeName;

public interface CustomerPublicEvent {

  @TypeName("customer-created")
  record Created(String email, String name) implements CustomerPublicEvent {}

  @TypeName("name-changed")
  record NameChanged(String newName) implements CustomerPublicEvent {}
}
