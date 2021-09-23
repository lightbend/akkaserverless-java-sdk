package com.example.fibonacci.fibonacci

import com.akkaserverless.scalasdk.AkkaServerless
import org.slf4j.LoggerFactory

object Main {

  private val log = LoggerFactory.getLogger("com.example.fibonacci.fibonacci.Main")

  def createAkkaServerless(): AkkaServerless = {
    // The AkkaServerlessFactory automatically registers any generated Actions, Views or Entities,
    // and is kept up-to-date with any changes in your protobuf definitions.
    // If you prefer, you may remove this and manually register these components in a
    // `AkkaServerless()` instance.
    AkkaServerlessFactory.withComponents(
      new FibonacciAction(_))
  }

  def main(args: Array[String]): Unit = {
    log.info("starting the Akka Serverless service")
    createAkkaServerless().start()
  }
}
