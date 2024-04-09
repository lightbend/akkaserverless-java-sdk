/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.view;

import kalix.javasdk.impl.ComponentOptions;
import kalix.javasdk.impl.view.ViewOptionsImpl;

import java.util.Collections;

public interface ViewOptions extends ComponentOptions {

  /** Create default options for a view. */
  static ViewOptions defaults() {
    return new ViewOptionsImpl(Collections.emptySet());
  }

  /**
   * @return the headers requested to be forwarded as metadata (cannot be mutated, use
   *     withForwardHeaders)
   */
  java.util.Set<String> forwardHeaders();

  /**
   * Ask Kalix to forward these headers from the incoming request as metadata headers for the
   * incoming commands. By default, no headers except "X-Server-Timing" are forwarded.
   */
  ViewOptions withForwardHeaders(java.util.Set<String> headers);
}
