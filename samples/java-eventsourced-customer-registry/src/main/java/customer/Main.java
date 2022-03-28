/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package customer;

import kalix.javasdk.Kalix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import customer.domain.CustomerEntity;
import customer.view.CustomerByNameView;

public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  // tag::register[]
  public static Kalix createAkkaServerless() {
    return AkkaServerlessFactory.withComponents(
      CustomerEntity::new,
      CustomerByNameView::new);
  }
  // end::register[]

  public static void main(String[] args) throws Exception {
    LOG.info("starting the Akka Serverless service");
    createAkkaServerless().start();
  }
}
