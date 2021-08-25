/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package customer;

import com.akkaserverless.javasdk.AkkaServerless;
import customer.action.CustomerActionImpl;
import customer.action.CustomerActionProvider;
import customer.domain.CustomerDomain;
import customer.domain.CustomerValueEntityProvider;
import customer.view.CustomerViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import customer.domain.CustomerValueEntity;

public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  public static AkkaServerless createAkkaServerless() {
    // tag::register[]
    return new AkkaServerless()
            .registerView(
                    CustomerViewModel.getDescriptor().findServiceByName("CustomerByName"),
                    "customerByName",
                    CustomerDomain.getDescriptor())
            // end::register[]
            .register(CustomerValueEntityProvider.of(CustomerValueEntity::new))
            .register(CustomerActionProvider.of(CustomerActionImpl::new));
  }

  public static void main(String[] args) throws Exception {
    LOG.info("starting the Akka Serverless service");
    createAkkaServerless().start();
  }
}
