/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.akkasls.codegen.java

import com.lightbend.akkasls.codegen.TestData

import munit.Location

class ValueEntitySourceGeneratorSuite extends munit.FunSuite {
  private val testData = TestData.javaStyle

  test("ValueEntity source") {

    val service = testData.simpleEntityService()
    val entity = testData.valueEntity()

    val packageName = "com.example.service"
    val className = "MyService"

    val generatedSrc =
      ValueEntitySourceGenerator.valueEntitySource(service, entity, packageName, className)

    assertNoDiff(
      generatedSrc,
      """package com.example.service;
         |
         |import com.akkaserverless.javasdk.valueentity.ValueEntityContext;
         |import com.example.service.domain.EntityOuterClass;
         |import com.external.Empty;
         |
         |// This class was initially generated based on the .proto definition by Akka Serverless tooling.
         |//
         |// As long as this file exists it will not be overwritten: you can maintain it yourself,
         |// or delete it so it is regenerated as needed.
         |
         |/** A value entity. */
         |public class MyService extends AbstractMyService {
         |  @SuppressWarnings("unused")
         |  private final String entityId;
         |
         |  public MyService(ValueEntityContext context) {
         |    this.entityId = context.entityId();
         |  }
         |
         |  @Override
         |  public EntityOuterClass.MyState emptyState() {
         |    throw new UnsupportedOperationException("Not implemented yet, replace with your empty entity state");
         |  }
         |
         |  @Override
         |  public Effect<Empty> set(EntityOuterClass.MyState currentState, ServiceOuterClass.SetValue setValue) {
         |    return effects().error("The command handler for `Set` is not implemented, yet");
         |  }
         |
         |  @Override
         |  public Effect<ServiceOuterClass.MyState> get(EntityOuterClass.MyState currentState, ServiceOuterClass.GetValue getValue) {
         |    return effects().error("The command handler for `Get` is not implemented, yet");
         |  }
         |}
         |""".stripMargin)
  }

  test("Abstract ValueEntity source") {
    val service = testData.simpleEntityService()
    val entity = testData.valueEntity()
    val packageName = "com.example.service"
    val className = "MyService"

    val generatedSrc =
      EntityServiceSourceGenerator.interfaceSource(service, entity, packageName, className)
    assertNoDiff(
      generatedSrc,
      """package com.example.service;
         |
         |import com.akkaserverless.javasdk.valueentity.ValueEntity;
         |import com.example.service.domain.EntityOuterClass;
         |import com.external.Empty;
         |
         |// This code is managed by Akka Serverless tooling.
         |// It will be re-generated to reflect any changes to your protobuf definitions.
         |// DO NOT EDIT
         |
         |/** A value entity. */
         |public abstract class AbstractMyService extends ValueEntity<EntityOuterClass.MyState> {
         |
         |  /** Command handler for "Set". */
         |  public abstract Effect<Empty> set(EntityOuterClass.MyState currentState, ServiceOuterClass.SetValue setValue);
         |
         |  /** Command handler for "Get". */
         |  public abstract Effect<ServiceOuterClass.MyState> get(EntityOuterClass.MyState currentState, ServiceOuterClass.GetValue getValue);
         |
         |}
         |""".stripMargin)
  }

  test("ValueEntity generated handler") {
    val service = testData.simpleEntityService()
    val entity = testData.valueEntity()
    val packageName = "com.example.service"
    val className = "MyService"

    val generatedSrc =
      ValueEntitySourceGenerator.valueEntityRouter(service, entity, packageName, className)

    assertNoDiff(
      generatedSrc,
      """package com.example.service;
         |
         |import com.akkaserverless.javasdk.impl.valueentity.ValueEntityRouter;
         |import com.akkaserverless.javasdk.valueentity.CommandContext;
         |import com.akkaserverless.javasdk.valueentity.ValueEntity;
         |import com.example.service.domain.EntityOuterClass;
         |import com.external.Empty;
         |
         |// This code is managed by Akka Serverless tooling.
         |// It will be re-generated to reflect any changes to your protobuf definitions.
         |// DO NOT EDIT
         |
         |/**
         | * A value entity handler that is the glue between the Protobuf service <code>MyService</code>
         | * and the command handler methods in the <code>MyValueEntity</code> class.
         | */
         |public class MyServiceRouter extends ValueEntityRouter<EntityOuterClass.MyState, MyValueEntity> {
         |
         |  public MyServiceRouter(MyValueEntity entity) {
         |    super(entity);
         |  }
         |
         |  @Override
         |  public ValueEntity.Effect<?> handleCommand(
         |      String commandName, EntityOuterClass.MyState state, Object command, CommandContext context) {
         |    switch (commandName) {
         |
         |      case "Set":
         |        return entity().set(state, (ServiceOuterClass.SetValue) command);
         |
         |      case "Get":
         |        return entity().get(state, (ServiceOuterClass.GetValue) command);
         |
         |      default:
         |        throw new ValueEntityRouter.CommandHandlerNotFound(commandName);
         |    }
         |  }
         |}
         |""".stripMargin)
  }

  test("ValueEntity Provider") {
    val service = testData.simpleEntityService()
    val entity = testData.valueEntity()

    val packageName = "com.example.service"
    val className = "MyService"

    val generatedSrc =
      ValueEntitySourceGenerator.valueEntityProvider(service, entity, packageName, className)

    assertNoDiff(
      generatedSrc,
      """package com.example.service;
         |
         |import com.akkaserverless.javasdk.valueentity.ValueEntityContext;
         |import com.akkaserverless.javasdk.valueentity.ValueEntityOptions;
         |import com.akkaserverless.javasdk.valueentity.ValueEntityProvider;
         |import com.example.service.domain.EntityOuterClass;
         |import com.external.Empty;
         |import com.external.ExternalDomain;
         |import com.google.protobuf.Descriptors;
         |import java.util.function.Function;
         |
         |// This code is managed by Akka Serverless tooling.
         |// It will be re-generated to reflect any changes to your protobuf definitions.
         |// DO NOT EDIT
         |
         |/**
         | * A value entity provider that defines how to register and create the entity for
         | * the Protobuf service <code>MyService</code>.
         | *
         | * Should be used with the <code>register</code> method in {@link com.akkaserverless.javasdk.AkkaServerless}.
         | */
         |public class MyServiceProvider implements ValueEntityProvider<EntityOuterClass.MyState, MyService> {
         |
         |  private final Function<ValueEntityContext, MyService> entityFactory;
         |  private final ValueEntityOptions options;
         |
         |  /** Factory method of MyServiceProvider */
         |  public static MyServiceProvider of(Function<ValueEntityContext, MyService> entityFactory) {
         |    return new MyServiceProvider(entityFactory, ValueEntityOptions.defaults());
         |  }
         | 
         |  private MyServiceProvider(
         |      Function<ValueEntityContext, MyService> entityFactory,
         |      ValueEntityOptions options) {
         |    this.entityFactory = entityFactory;
         |    this.options = options;
         |  }
         |
         |  @Override
         |  public final ValueEntityOptions options() {
         |    return options;
         |  }
         | 
         |  public final MyServiceProvider withOptions(ValueEntityOptions options) {
         |    return new MyServiceProvider(entityFactory, options);
         |  }
         |
         |  @Override
         |  public final Descriptors.ServiceDescriptor serviceDescriptor() {
         |    return ServiceOuterClass.getDescriptor().findServiceByName("MyService");
         |  }
         |
         |  @Override
         |  public final String entityType() {
         |    return "MyValueEntity";
         |  }
         |
         |  @Override
         |  public final MyServiceRouter newRouter(ValueEntityContext context) {
         |    return new MyServiceRouter(entityFactory.apply(context));
         |  }
         |
         |  @Override
         |  public final Descriptors.FileDescriptor[] additionalDescriptors() {
         |    return new Descriptors.FileDescriptor[] {
         |      EntityOuterClass.getDescriptor(),
         |      ExternalDomain.getDescriptor(),
         |      ServiceOuterClass.getDescriptor()
         |    };
         |  }
         |}
         |""".stripMargin)
  }

}
