/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package customer;

import kalix.javasdk.Kalix;
import customer.domain.CustomerEntity;
import customer.domain.CustomerEntityProvider;
import customer.view.CustomerByNameView;
import customer.view.CustomerByNameViewProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MainWithCustomViewId {

  private static final Logger LOG = LoggerFactory.getLogger(MainWithCustomViewId.class);

  // tag::register[]
  public static Kalix createAkkaServerless() {
    Kalix kalix = new Kalix();
    kalix.register(CustomerByNameViewProvider.of(CustomerByNameView::new)
        .withViewId("CustomerByNameV2"));
    kalix.register(CustomerEntityProvider.of(CustomerEntity::new));
    return kalix;
  }
  // end::register[]

  public static void main(String[] args) throws Exception {
    LOG.info("starting the Akka Serverless service");
    createAkkaServerless().start();
  }
}
