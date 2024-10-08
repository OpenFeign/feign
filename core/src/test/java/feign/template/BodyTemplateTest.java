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
package feign.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.junit.jupiter.api.Test;

class BodyTemplateTest {

  @Test
  void bodyTemplatesSupportJsonOnlyWhenEncoded() {
    String bodyTemplate =
        "%7B\"resize\": %7B\"method\": \"fit\",\"width\": {size},\"height\": {size}%7D%7D";
    BodyTemplate template = BodyTemplate.create(bodyTemplate);
    String expanded = template.expand(Collections.singletonMap("size", "100"));
    assertThat(expanded)
        .isEqualToIgnoringCase(
            "{\"resize\": {\"method\": \"fit\",\"width\": 100,\"height\": 100}}");
  }
}
