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
 * Annotation for marking a method under EventSourced entities as handlers for events of such entity.
 *
 * A method marked with this annotation needs to:
 * <p>
 * <ul>
 * <li>be public</li>
 * <li>have exactly one parameter</li>
 * <li>have a return type that matches the state type of the enclosing entity</li>
 * </ul>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventHandler {}
