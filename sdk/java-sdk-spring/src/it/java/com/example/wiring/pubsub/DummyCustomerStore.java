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

package com.example.wiring.pubsub;

import com.example.wiring.valueentities.customer.CustomerEntity;

import java.util.concurrent.ConcurrentHashMap;

public class DummyCustomerStore {

  private static ConcurrentHashMap<String, CustomerEntity.Customer> customers = new ConcurrentHashMap<>();

  public static void store(String entityId, CustomerEntity.Customer customer) {
    customers.put(entityId, customer);
  }

  public static CustomerEntity.Customer get(String entityId) {
    return customers.get(entityId);
  }
}
