/**
 * Copyright 2012-2018 The Feign Authors
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
package feign;

import feign.querymap.FieldQueryMapEncoder;
import feign.querymap.BeanQueryMapEncoder;
import java.util.Map;

/**
 * A QueryMapEncoder encodes Objects into maps of query parameter names to values.
 *
 * @see FieldQueryMapEncoder
 * @see BeanQueryMapEncoder
 *
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
   * @deprecated use {@link BeanQueryMapEncoder} instead. default encoder uses reflection to inspect
   *             provided objects Fields to expand the objects values into a query string. If you
   *             prefer that the query string be built using getter and setter methods, as defined
   *             in the Java Beans API, please use the {@link BeanQueryMapEncoder}
   */
  class Default extends FieldQueryMapEncoder {
  }
}
