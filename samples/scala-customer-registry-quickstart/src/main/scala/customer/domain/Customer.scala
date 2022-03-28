/* This code was generated by Kalix tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package customer.domain

import kalix.scalasdk.valueentity.ValueEntity
import kalix.scalasdk.valueentity.ValueEntityContext
import com.google.protobuf.empty.Empty
import customer.api

class Customer(context: ValueEntityContext) extends AbstractCustomer {

  override def emptyState: CustomerState = CustomerState()

  // tag::create[]
  override def create(currentState: CustomerState, customer: api.Customer): ValueEntity.Effect[Empty] = {
    val state = convertToDomain(customer)
    effects.updateState(state).thenReply(Empty.defaultInstance)
  }

  def convertToDomain(customer: api.Customer): CustomerState =
    CustomerState(
      customerId = customer.customerId,
      email = customer.email,
      name = customer.name,
      address = customer.address.map(convertToDomain)
    )

  def convertToDomain(address: api.Address): Address =
    Address(
      street = address.street,
      city = address.city
    )
  // end::create[]

  // tag::getCustomer[]
  override def getCustomer(currentState: CustomerState, getCustomerRequest: api.GetCustomerRequest): ValueEntity.Effect[api.Customer] =
    if (currentState.customerId == "") {
      effects.error(s"Customer ${getCustomerRequest.customerId} has not been created.")
    } else {
      effects.reply(convertToApi(currentState))
    }

  def convertToApi(customer: CustomerState): api.Customer =
    api.Customer(
      customerId = customer.customerId,
      email = customer.email,
      name = customer.name
    )
  // end::getCustomer[]

  override def changeName(currentState: CustomerState, changeNameRequest: api.ChangeNameRequest): ValueEntity.Effect[Empty] =
    effects.updateState(currentState.copy(name = changeNameRequest.newName)).thenReply(Empty.defaultInstance)

  override def changeAddress(currentState: CustomerState, changeAddressRequest: api.ChangeAddressRequest): ValueEntity.Effect[Empty] =
    effects.updateState(currentState.copy(address = changeAddressRequest.newAddress.map(convertToDomain)))
      .thenReply(Empty.defaultInstance)
}
