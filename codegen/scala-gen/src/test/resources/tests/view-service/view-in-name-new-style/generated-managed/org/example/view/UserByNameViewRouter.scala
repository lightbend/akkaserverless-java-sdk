package org.example.view

import kalix.javasdk.impl.view.UpdateHandlerNotFound
import kalix.scalasdk.impl.view.ViewRouter
import kalix.scalasdk.view.View

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

class UserByNameViewRouter(view: UserByNameViewImpl)
  extends ViewRouter[UserState, UserByNameViewImpl](view) {

  override def handleUpdate(
      eventName: String,
      state: UserState,
      event: Any): View.UpdateEffect[UserState] = {

    eventName match {


      case _ =>
        throw new UpdateHandlerNotFound(eventName)
    }
  }

}
