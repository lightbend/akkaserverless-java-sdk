/* This code was initialised by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package customer.view;

import com.akkaserverless.javasdk.view.View;
import customer.domain.CustomerDomain;
import java.util.Optional;

/** A view. */
@View
public class CustomerByName extends AbstractCustomerByName {
    @Override
    public CustomerDomain.CustomerState updateCustomer(CustomerDomain.CustomerState event, Optional<CustomerDomain.CustomerState> state) {
        return state.orElseThrow(
                () ->
                        new RuntimeException(
                                "Received `" + event.getClass().getSimpleName() + "`, but no state exists."));
    }
}
