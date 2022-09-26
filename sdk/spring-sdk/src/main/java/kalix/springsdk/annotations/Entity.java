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

package kalix.springsdk.annotations;

import java.lang.annotation.*;

/**
 * <p>Annotation for providing required type and key for any Kalix Entity.</p>
 *
 *
 * <b>Note:</b> can only be used at type level.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Entity {

  /**
   * Assign a type to the entity. This name should be unique among the different existing entities within a Kalix application.
   */
  String entityType();

  /**
   * Assign a key to the entity. This should be unique per entity and map to some field being received on the route path.
   */
  String[] entityKey();
}
