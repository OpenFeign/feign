/*
 * Copyright 2013 Netflix, Inc.
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
package feign;

import static java.lang.String.format;

import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/** Utilities, typically copied in from guava, so as to avoid dependency conflicts. */
public class Util {
  private Util() { // no instances
  }

  // feign.Util
  /** The HTTP Accept header field name. */
  public static final String ACCEPT = "Accept";

  /** The HTTP Content-Length header field name. */
  public static final String CONTENT_LENGTH = "Content-Length";

  /** The HTTP Content-Type header field name. */
  public static final String CONTENT_TYPE = "Content-Type";

  /** The HTTP Host header field name. */
  public static final String HOST = "Host";

  /** The HTTP Location header field name. */
  public static final String LOCATION = "Location";

  /** The HTTP Retry-After header field name. */
  public static final String RETRY_AFTER = "Retry-After";

  // com.google.common.base.Charsets
  /** UTF-8: eight-bit UCS Transformation Format. */
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  /** Copy of {@code com.google.common.base.Preconditions#checkArgument}. */
  public static void checkArgument(
      boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /** Copy of {@code com.google.common.base.Preconditions#checkNotNull}. */
  public static <T> T checkNotNull(
      T reference, String errorMessageTemplate, Object... errorMessageArgs) {
    if (reference == null) {
      // If either of these parameters is null, the right thing happens anyway
      throw new NullPointerException(format(errorMessageTemplate, errorMessageArgs));
    }
    return reference;
  }

  /** Copy of {@code com.google.common.base.Preconditions#checkState}. */
  public static void checkState(
      boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalStateException(format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /** Adapted from {@code com.google.common.base.Strings#emptyToNull}. */
  public static String emptyToNull(String string) {
    return string == null || string.isEmpty() ? null : string;
  }

  public static String join(char separator, String... parts) {
    if (parts == null || parts.length == 0) return "";
    StringBuilder to = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      to.append(parts[i]);
      if (i + 1 < parts.length) {
        to.append(separator);
      }
    }
    return to.toString();
  }

  /** Adapted from {@code com.google.common.base.Strings#emptyToNull}. */
  public static <T> T[] toArray(Iterable<? extends T> iterable, Class<T> type) {
    Collection<T> collection;
    if (iterable instanceof Collection) {
      collection = (Collection<T>) iterable;
    } else {
      collection = new ArrayList<T>();
      for (T element : iterable) {
        collection.add(element);
      }
    }
    T[] array = (T[]) Array.newInstance(type, collection.size());
    return collection.toArray(array);
  }

  /** Returns an unmodifiable collection which may be empty, but is never null. */
  public static <T> Collection<T> valuesOrEmpty(Map<String, Collection<T>> map, String key) {
    return map.containsKey(key) ? map.get(key) : Collections.<T>emptyList();
  }

  public static <T> T firstOrNull(Map<String, Collection<T>> map, String key) {
    if (map.containsKey(key) && !map.get(key).isEmpty()) {
      return map.get(key).iterator().next();
    }
    return null;
  }
}
