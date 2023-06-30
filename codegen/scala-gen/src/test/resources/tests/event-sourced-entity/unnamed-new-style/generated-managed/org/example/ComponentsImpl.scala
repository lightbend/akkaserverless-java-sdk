package org.example

import akka.grpc.scaladsl.SingleResponseRequestBuilder
import kalix.scalasdk.Context
import kalix.scalasdk.DeferredCall
import kalix.scalasdk.Metadata
import kalix.scalasdk.impl.InternalContext
import kalix.scalasdk.impl.ScalaDeferredCallAdapter


// This code is managed by Kalix tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * Not intended for direct instantiation, called by generated code, use Action.components() to access
 */
final class ComponentsImpl(context: InternalContext) extends Components {

  def this(context: Context) =
    this(context.asInstanceOf[InternalContext])

  private def getGrpcClient[T](serviceClass: Class[T]): T =
    context.getComponentGrpcClient(serviceClass)

  private def addHeaders[Req, Res](
      requestBuilder: SingleResponseRequestBuilder[Req, Res],
      metadata: Metadata): SingleResponseRequestBuilder[Req, Res] = {
    metadata.filter(_.isText).foldLeft(requestBuilder) { (builder, entry) =>
      builder.addHeader(entry.key, entry.value)
    }
  }

 @Override
 override def counterServiceEntity: Components.CounterServiceEntityCalls =
   new CounterServiceEntityCallsImpl();


 private final class CounterServiceEntityCallsImpl extends Components.CounterServiceEntityCalls {
   override def increase(command: _root_.org.example.eventsourcedentity.IncreaseValue): DeferredCall[_root_.org.example.eventsourcedentity.IncreaseValue, _root_.com.google.protobuf.empty.Empty] =
     ScalaDeferredCallAdapter(
       command,
       Metadata.empty,
       "org.example.eventsourcedentity.CounterService",
       "Increase",
       (metadata: Metadata) => addHeaders(getGrpcClient(classOf[_root_.org.example.eventsourcedentity.CounterService])
         .asInstanceOf[_root_.org.example.eventsourcedentity.CounterServiceClient].increase(), metadata).invoke(command)
     )
   override def decrease(command: _root_.org.example.eventsourcedentity.DecreaseValue): DeferredCall[_root_.org.example.eventsourcedentity.DecreaseValue, _root_.com.google.protobuf.empty.Empty] =
     ScalaDeferredCallAdapter(
       command,
       Metadata.empty,
       "org.example.eventsourcedentity.CounterService",
       "Decrease",
       (metadata: Metadata) => addHeaders(getGrpcClient(classOf[_root_.org.example.eventsourcedentity.CounterService])
         .asInstanceOf[_root_.org.example.eventsourcedentity.CounterServiceClient].decrease(), metadata).invoke(command)
     )
 }

}
