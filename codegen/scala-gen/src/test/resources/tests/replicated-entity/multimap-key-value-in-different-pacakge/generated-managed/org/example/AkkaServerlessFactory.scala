package org.example

import com.akkaserverless.scalasdk.AkkaServerless
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntityContext
import com.example.replicated.multimap.domain.SomeMultiMap
import com.example.replicated.multimap.domain.SomeMultiMapProvider

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

object AkkaServerlessFactory {

  def withComponents(
      createSomeMultiMap: ReplicatedEntityContext => SomeMultiMap): AkkaServerless = {
    val akkaServerless = AkkaServerless()
    akkaServerless
      .register(SomeMultiMapProvider(createSomeMultiMap))
  }
}
