/*
 * Copyright 2018 Artem Labazin
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

package feign.form.spring.converter;

import static lombok.AccessLevel.PRIVATE;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import lombok.experimental.Delegate;
import lombok.experimental.FieldDefaults;

/**
 * A Map<String, String> implementation that normalizes the key to UPPER CASE, so
 * that value retrieval via the key is case insensitive.
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
final class IgnoreKeyCaseMap implements Map<String, String> {

  @Delegate(excludes = OverrideMap.class)
  HashMap<String, String> delegate = new HashMap<String, String>();

  @Override
  public boolean containsKey (Object key) {
    return delegate.containsKey(normalizeKey(key));
  }

  @Override
  public String get (Object key) {
    return delegate.get(normalizeKey(key));
  }

  @Override
  public String put (String key, String value) {
    return delegate.put(normalizeKey(key), value);
  }

  @Override
  public String remove (Object key) {
    return delegate.remove(normalizeKey(key));
  }

  private static String normalizeKey (Object key) {
    return key != null
           ? key.toString().toUpperCase(new Locale("en_US"))
           : null;
  }

  private interface OverrideMap {

    boolean containsKey (Object key);

    String get (Object key);

    String put (String key, String value);

    String remove (Object key);
  }
}
