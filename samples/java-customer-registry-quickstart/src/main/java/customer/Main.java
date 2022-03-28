/* This code was generated by Kalix tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package customer;

import kalix.javasdk.Kalix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import customer.domain.Customer;

public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  public static Kalix createKalix() {
    // The KalixFactory automatically registers any generated Actions, Views or Entities,
    // and is kept up-to-date with any changes in your protobuf definitions.
    // If you prefer, you may remove this and manually register these components in a
    // `new Kalix()` instance.
    return KalixFactory.withComponents(
      Customer::new);
  }

  public static void main(String[] args) throws Exception {
    LOG.info("starting the Kalix service");
    createKalix().start();
  }
}
