/*
 * Copyright 2019 the original author or authors.
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

import java.util.HashMap;
import java.util.Locale;

/**
 * A Map<String, String> implementation that normalizes the key to UPPER CASE, so
 * that value retrieval via the key is case insensitive.
 */
final class IgnoreKeyCaseMap extends HashMap<String, String> {

  private static final long serialVersionUID = -2321516556941546746L;

  private static String normalizeKey (Object key) {
    return key == null
           ? null
           : key.toString().toUpperCase(new Locale("en_US"));
  }

  @Override
  public boolean containsKey (Object key) {
    return super.containsKey(normalizeKey(key));
  }

  @Override
  public String get (Object key) {
    return super.get(normalizeKey(key));
  }

  @Override
  public String put (String key, String value) {
    return super.put(normalizeKey(key), value);
  }

  @Override
  public String remove (Object key) {
    return super.remove(normalizeKey(key));
  }
}
