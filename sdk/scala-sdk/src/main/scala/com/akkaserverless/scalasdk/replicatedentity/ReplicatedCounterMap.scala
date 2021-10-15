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

package com.akkaserverless.scalasdk.replicatedentity

import scala.collection.immutable.Set

import com.akkaserverless.javasdk.impl.replicatedentity.ReplicatedCounterMapImpl
import com.akkaserverless.replicatedentity.ReplicatedData

/**
 * A Map of counters. Uses [[ReplicatedCounter]] 's as values.
 *
 * @tparam K
 *   The type for keys.
 */
class ReplicatedCounterMap[K] private[scalasdk] (override val _internal: ReplicatedCounterMapImpl[K])
    extends ReplicatedData {

  /**
   * Optionally returns the value associated with a key.
   *
   * @param key
   *   the key value
   * @return
   *   an option value containing the value associated with `key` in this map, or `None` if none exists.
   */

  def get(key: K): Option[Long] = _internal.getOption(key)

  /**
   * Get the counter value for the given key.
   *
   * @param key
   *   the key to get the value for
   * @return
   *   the current value of the counter at that key, or zero if no counter exists
   */
  def apply(key: K): Long = _internal.get(key)

  /**
   * Increment the counter at the given key by the given amount.
   *
   * <p>The counter will be created if it is not already in the map.
   *
   * @param key
   *   the key of the counter
   * @param amount
   *   the amount to increment by
   * @return
   *   a new counter map with the incremented value
   */
  def increment(key: K, amount: Long): ReplicatedCounterMap[K] =
    new ReplicatedCounterMap(_internal.increment(key, amount))

  /**
   * Decrement the counter at the given key by the given amount.
   *
   * <p>The counter will be created if it is not already in the map.
   *
   * @param key
   *   the key of the counter
   * @param amount
   *   the amount to decrement by
   * @return
   *   a new counter map with the decremented value
   */
  def decrement(key: K, amount: Long): ReplicatedCounterMap[K] =
    new ReplicatedCounterMap(_internal.decrement(key, amount))

  /**
   * Remove the mapping for a key if it is present.
   *
   * @param key
   *   key whose mapping is to be removed from the map
   * @return
   *   a new counter map with the removed mapping
   */
  def remove(key: K): ReplicatedCounterMap[K] =
    new ReplicatedCounterMap(_internal.remove(key))

  /**
   * Remove all mappings from this counter map.
   *
   * @return
   *   a new empty counter map
   */
  def clear(): ReplicatedCounterMap[K] =
    new ReplicatedCounterMap(_internal.clear())

  /**
   * Get the number of key-counter mappings in this counter map.
   *
   * @return
   *   the number of key-counter mappings in this counter map
   */
  def size: Int = _internal.size

  /**
   * Check whether this counter map is empty.
   *
   * @return
   *   `true` if this counter map contains no key-counter mappings
   */
  def isEmpty: Boolean = _internal.isEmpty

  /**
   * Check whether this counter map contains a mapping for the given key.
   *
   * @param key
   *   key whose presence in this map is to be tested
   * @return
   *   `true` if this counter map contains a mapping for the given key
   */
  def contains(key: K): Boolean = _internal.containsKey(key)

  /**
   * Tests whether a predicate holds for all elements of this ReplicatedCounterMap.
   *
   * @param predicate
   *   the predicate used to test elements.
   * @return
   *   `true` if this ReplicatedCounterMap is empty or the given predicate `pred` holds for all elements of this
   *   ReplicatedCounterMap, otherwise `false`.
   */
  def forall(predicate: ((K, Long)) => Boolean): Boolean =
    _internal.forall(predicate)

  /**
   * Get a [[Set]] view of the keys contained in this counter map.
   *
   * @return
   *   the keys contained in this counter map
   */
  def keySet: Set[K] = _internal.keys

}
