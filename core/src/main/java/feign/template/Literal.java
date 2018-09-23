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

/**
 * URI Template Literal.
 */
class Literal implements TemplateChunk {

  private final String value;

  /**
   * Create a new Literal.
   *
   * @param value of the literal.
   * @return the new Literal.
   */
  public static Literal create(String value) {
    return new Literal(value);
  }

  /**
   * Create a new Literal.
   *
   * @param value of the literal.
   */
  Literal(String value) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("a value is required.");
    }
    this.value = value;
  }

  @Override
  public String getValue() {
    return this.value;
  }
}
