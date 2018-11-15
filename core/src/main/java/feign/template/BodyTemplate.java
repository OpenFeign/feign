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

import feign.Util;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Template for @{@link feign.Body} annotated Templates. Unresolved expressions are preserved as
 * literals and literals are not URI encoded.
 */
public final class BodyTemplate extends Template {

  /**
   * Create a new Body Template.
   *
   * @param template to parse.
   * @return a Body Template instance.
   */
  public static BodyTemplate create(String template) {
    return new BodyTemplate(template, Util.UTF_8);
  }

  private BodyTemplate(String value, Charset charset) {
    super(value, ExpansionOptions.ALLOW_UNRESOLVED, EncodingOptions.NOT_REQUIRED, false, charset);
  }

  @Override
  public String expand(Map<String, ?> variables) {
    return UriUtils.decode(super.expand(variables), Util.UTF_8);
  }
}
