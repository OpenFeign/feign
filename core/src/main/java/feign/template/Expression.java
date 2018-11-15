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

package feign.template;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URI Template Expression.
 */
abstract class Expression implements TemplateChunk {

  private String name;
  private Pattern pattern;

  /**
   * Create a new Expression.
   *
   * @param name of the variable
   * @param pattern the resolved variable must adhere to, optional.
   */
  Expression(String name, String pattern) {
    this.name = name;
    Optional.ofNullable(pattern).ifPresent(s -> this.pattern = Pattern.compile(s));
  }

  abstract String expand(Object variable, boolean encode);

  public String getName() {
    return this.name;
  }

  Pattern getPattern() {
    return pattern;
  }

  /**
   * Checks if the provided value matches the variable pattern, if one is defined. Always true if no
   * pattern is defined.
   *
   * @param value to check.
   * @return true if it matches.
   */
  boolean matches(String value) {
    if (pattern == null) {
      return true;
    }
    return pattern.matcher(value).matches();
  }

  @Override
  public String getValue() {
    if (this.pattern != null) {
      return "{" + this.name + ":" + this.pattern + "}";
    }
    return "{" + this.name + "}";
  }

  @Override
  public String toString() {
    return this.getValue();
  }
}
