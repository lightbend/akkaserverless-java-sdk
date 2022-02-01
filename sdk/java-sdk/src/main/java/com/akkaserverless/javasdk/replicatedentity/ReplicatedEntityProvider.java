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

package com.akkaserverless.javasdk.replicatedentity;

import com.akkaserverless.javasdk.impl.Serializer;
import com.akkaserverless.javasdk.impl.replicatedentity.ReplicatedEntityRouter;
import com.akkaserverless.replicatedentity.ReplicatedData;
import com.google.protobuf.Descriptors;

/**
 * Register a value based entity in {@link com.akkaserverless.javasdk.AkkaServerless} using a <code>
 *  ReplicatedEntityProvider</code>. The concrete <code>ReplicatedEntityProvider</code> is generated
 * for the specific entities defined in Protobuf.
 */
public interface ReplicatedEntityProvider<D extends ReplicatedData, E extends ReplicatedEntity<D>> {

  ReplicatedEntityOptions options();

  Descriptors.ServiceDescriptor serviceDescriptor();

  String entityType();

  ReplicatedEntityRouter<D, E> newRouter(ReplicatedEntityContext context);

  Descriptors.FileDescriptor[] additionalDescriptors();

  Serializer serializer();
}
