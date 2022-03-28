package org.example.service

import kalix.scalasdk.action.Action
import kalix.scalasdk.action.ActionCreationContext
import com.google.protobuf.empty.Empty

// This class was initially generated based on the .proto definition by Akka Serverless tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

class MyServiceActionImpl(creationContext: ActionCreationContext) extends AbstractMyServiceAction {

  override def simpleMethod(myRequest: MyRequest): Action.Effect[Empty] = {
    throw new RuntimeException("The command handler for `simpleMethod` is not implemented, yet")
  }
}

