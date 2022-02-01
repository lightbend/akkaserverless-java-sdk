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

package com.akkaserverless.javasdk.tck.model.replicatedentity;

import com.akkaserverless.javasdk.impl.Serializer;
import com.akkaserverless.replicatedentity.ReplicatedData;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityContext;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityOptions;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityProvider;
import com.akkaserverless.tck.model.ReplicatedEntity;
import com.google.protobuf.Descriptors;
import com.google.protobuf.EmptyProto;

import java.util.function.Function;

public class ReplicatedEntityTckModelEntityProvider
    implements ReplicatedEntityProvider<ReplicatedData, ReplicatedEntityTckModelEntity> {

  private final Function<ReplicatedEntityContext, ReplicatedEntityTckModelEntity> entityFactory;
  private final ReplicatedEntityOptions options;

  public static ReplicatedEntityTckModelEntityProvider of(
      Function<ReplicatedEntityContext, ReplicatedEntityTckModelEntity> entityFactory) {
    return new ReplicatedEntityTckModelEntityProvider(
        entityFactory, ReplicatedEntityOptions.defaults());
  }

  private ReplicatedEntityTckModelEntityProvider(
      Function<ReplicatedEntityContext, ReplicatedEntityTckModelEntity> entityFactory,
      ReplicatedEntityOptions options) {
    this.entityFactory = entityFactory;
    this.options = options;
  }

  @Override
  public final ReplicatedEntityOptions options() {
    return options;
  }

  public final ReplicatedEntityTckModelEntityProvider withOptions(ReplicatedEntityOptions options) {
    return new ReplicatedEntityTckModelEntityProvider(entityFactory, options);
  }

  @Override
  public final Descriptors.ServiceDescriptor serviceDescriptor() {
    return ReplicatedEntity.getDescriptor().findServiceByName("ReplicatedEntityTckModel");
  }

  @Override
  public final String entityType() {
    return "replicated-entity-tck-model";
  }

  @Override
  public final ReplicatedEntityTckModelEntityRouter newRouter(ReplicatedEntityContext context) {
    return new ReplicatedEntityTckModelEntityRouter(entityFactory.apply(context));
  }

  @Override
  public final Descriptors.FileDescriptor[] additionalDescriptors() {
    return new Descriptors.FileDescriptor[] {
      ReplicatedEntity.getDescriptor(), EmptyProto.getDescriptor()
    };
  }

  @Override
  public Serializer serializer() {
    return Serializer.noopSerializer();
  }
}
