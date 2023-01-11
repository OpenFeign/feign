/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.utils;

import java.util.HashSet;
import java.util.Set;

public class ExceptionUtils {
  /**
   * Introspects the {@link Throwable} to obtain the root cause.
   *
   * <p>
   * This method walks through the exception chain to the last element, "root" of the tree, using
   * {@link Throwable#getCause()}, and returns that exception.
   *
   * @param throwable the throwable to get the root cause for, may be null
   * @return the root cause of the {@link Throwable}, {@code null} if null throwable input
   */
  public static Throwable getRootCause(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    Throwable rootCause = throwable;
    // this is to avoid infinite loops for recursive cases
    final Set<Throwable> seenThrowables = new HashSet<>();
    seenThrowables.add(rootCause);
    while ((rootCause.getCause() != null && !seenThrowables.contains(rootCause.getCause()))) {
      seenThrowables.add(rootCause.getCause());
      rootCause = rootCause.getCause();
    }
    return rootCause;
  }
}
