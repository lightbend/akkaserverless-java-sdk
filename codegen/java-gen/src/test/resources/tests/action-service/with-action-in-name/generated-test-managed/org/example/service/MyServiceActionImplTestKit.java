package org.example.service;

import com.google.protobuf.Empty;
import kalix.javasdk.Metadata;
import kalix.javasdk.action.Action.Effect;
import kalix.javasdk.action.ActionCreationContext;
import kalix.javasdk.impl.action.ActionEffectImpl;
import kalix.javasdk.testkit.ActionResult;
import kalix.javasdk.testkit.impl.ActionResultImpl;
import kalix.javasdk.testkit.impl.TestKitActionContext;
import kalix.javasdk.testkit.impl.TestKitMockRegistry;
import org.example.service.MyServiceActionImpl;
import org.example.service.ServiceOuterClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// This code is managed by Kalix tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

public final class MyServiceActionImplTestKit {

  private Function<ActionCreationContext, MyServiceActionImpl> actionFactory;

  private TestKitMockRegistry mockRegistry;

  private MyServiceActionImpl createAction(TestKitActionContext context) {
    MyServiceActionImpl action = actionFactory.apply(context);
    action._internalSetActionContext(Optional.of(context));
    return action;
  }

  public static MyServiceActionImplTestKit of(Function<ActionCreationContext, MyServiceActionImpl> actionFactory) {
    return new MyServiceActionImplTestKit(actionFactory, TestKitMockRegistry.empty());
  }

  public static MyServiceActionImplTestKit of(Function<ActionCreationContext, MyServiceActionImpl> actionFactory, TestKitMockRegistry mockRegistry) {
    return new MyServiceActionImplTestKit(actionFactory, mockRegistry);
  }

  private MyServiceActionImplTestKit(Function<ActionCreationContext, MyServiceActionImpl> actionFactory, TestKitMockRegistry mockRegistry) {
    this.actionFactory = actionFactory;
    this.mockRegistry = mockRegistry;
  }

  private <E> ActionResult<E> interpretEffects(Effect<E> effect) {
    return new ActionResultImpl(effect);
  }

  public ActionResult<Empty> simpleMethod(ServiceOuterClass.MyRequest myRequest, Metadata metadata) {
    TestKitActionContext context = new TestKitActionContext(metadata, mockRegistry);
    Effect<Empty> effect = createAction(context).simpleMethod(myRequest);
    return interpretEffects(effect);
  }

  public ActionResult<Empty> simpleMethod(ServiceOuterClass.MyRequest myRequest) {
    return simpleMethod(myRequest, Metadata.EMPTY);
  }

}
