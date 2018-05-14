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

import java.lang.annotation.Retention;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A named template parameter applied to {@link Headers}, {@linkplain RequestLine} or
 * {@linkplain Body}
 */
@Retention(RUNTIME)
@java.lang.annotation.Target(PARAMETER)
public @interface Param {

  /**
   * The name of the template parameter.
   */
  String value();

  /**
   * How to expand the value of this parameter, if {@link ToStringExpander} isn't adequate.
   */
  Class<? extends Expander> expander() default ToStringExpander.class;

  /**
   * Specifies whether argument is already encoded The value is ignored for headers (headers are
   * never encoded)
   *
   * @see QueryMap#encoded
   */
  boolean encoded() default false;

  interface Expander {

    /**
     * Expands the value into a string. Does not accept or return null.
     */
    String expand(Object value);
  }

  final class ToStringExpander implements Expander {

    @Override
    public String expand(Object value) {
      return value.toString();
    }
  }
}
