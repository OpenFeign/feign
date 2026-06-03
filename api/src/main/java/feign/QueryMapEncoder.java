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
package feign;

import java.util.Map;

/**
 * A QueryMapEncoder encodes Objects into maps of query parameter names to values.
 *
 * @see feign.core.querymap.FieldQueryMapEncoder
 * @see feign.core.querymap.BeanQueryMapEncoder
 */
public interface QueryMapEncoder {

  /**
   * Encodes the given object into a query map.
   *
   * @param object the object to encode
   * @return the map represented by the object
   */
  Map<String, Object> encode(Object object);

  /**
   * @deprecated use {@link DefaultQueryMapEncoder} instead.
   */
  @Deprecated
  class Default implements QueryMapEncoder {
    private final QueryMapEncoder delegate;

    public Default() {
      QueryMapEncoder temp = null;
      try {
        temp =
            (QueryMapEncoder)
                Class.forName("feign.DefaultQueryMapEncoder")
                    .getDeclaredConstructor()
                    .newInstance();
      } catch (Exception e) {
        // ignore
      }
      this.delegate = temp;
    }

    @Override
    public Map<String, Object> encode(Object object) {
      if (delegate != null) {
        return delegate.encode(object);
      }
      return java.util.Collections.emptyMap();
    }
  }
}
