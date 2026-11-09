/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.form.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Elements {

  private Elements() {}

  public static Iterable<?> elementsOf(Object value) {
    if (value instanceof Iterable) {
      return (Iterable<?>) value;
    }
    if (value == null || !value.getClass().isArray()) {
      return Collections.emptyList();
    }
    int length = Array.getLength(value);
    List<Object> elements = new ArrayList<>(length);
    for (int index = 0; index < length; index++) {
      elements.add(Array.get(value, index));
    }
    return elements;
  }
}
