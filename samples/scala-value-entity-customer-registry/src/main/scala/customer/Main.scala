package customer

import com.akkaserverless.scalasdk.AkkaServerless
import customer.action.customer_action.CustomerActionImpl
import customer.domain.customer_domain.CustomerValueEntity
import customer.view.customer_view.CustomerByEmailView
import customer.view.customer_view.CustomerByNameView
import customer.view.customer_view.CustomerSummaryByNameView
import customer.view.customer_view.CustomersResponseByNameView
import org.slf4j.LoggerFactory

object Main {

  private val log = LoggerFactory.getLogger("customer.Main")

  def createAkkaServerless(): AkkaServerless = {
    // The AkkaServerlessFactory automatically registers any generated Actions, Views or Entities,
    // and is kept up-to-date with any changes in your protobuf definitions.
    // If you prefer, you may remove this and manually register these components in a
    // `AkkaServerless()` instance.
    AkkaServerlessFactory.withComponents(
      new CustomerValueEntity(_),
      new CustomerActionImpl(_),
      new CustomerByEmailView(_),
      new CustomerByNameView(_),
      new CustomerSummaryByNameView(_),
      new CustomersResponseByNameView(_))
  }

  def main(args: Array[String]): Unit = {
    log.info("starting the Akka Serverless service")
    createAkkaServerless().start()
  }
}
