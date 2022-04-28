package customer

import kalix.scalasdk.Kalix
import customer.domain.CustomerEntity
import customer.view.CustomerByNameView
import org.slf4j.LoggerFactory

object Main {

  private val log = LoggerFactory.getLogger("customer.Main")

  // tag::register[]
  def createKalix(): Kalix = {
    KalixFactory.withComponents(
      new CustomerEntity(_),
      new CustomerByNameView(_))
  }
  // end::register[]

  def main(args: Array[String]): Unit = {
    log.info("starting the Kalix service")
    createKalix().start()
  }
}
