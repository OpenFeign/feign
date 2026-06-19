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
package feign.form;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormEncoderCharsetTest {

  @Test
  void illegalCharsetInContentTypeFallsBackToUtf8() {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", "application/x-www-form-urlencoded; charset=_bad");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("foo", "bar");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.UTF_8)).isEqualTo("foo=bar");
  }

  @Test
  void unsupportedCharsetInContentTypeFallsBackToUtf8() {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", "application/x-www-form-urlencoded; charset=made-up-99");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("foo", "bar");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.UTF_8)).isEqualTo("foo=bar");
  }
}
