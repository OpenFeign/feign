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

  @Test
  void charsetParameterNameIsCaseInsensitive() {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", "application/x-www-form-urlencoded; Charset=ISO-8859-1");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "é");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.ISO_8859_1)).isEqualTo("name=%E9");
    assertThat(template.headers().get("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded; charset=ISO-8859-1");
  }

  @Test
  void quotedCharsetParameterIsHonored() {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", "application/x-www-form-urlencoded; charset=\"ISO-8859-1\"");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "é");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.ISO_8859_1)).isEqualTo("name=%E9");
    assertThat(template.headers().get("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded; charset=ISO-8859-1");
  }

  @Test
  void charsetTextInsideAnotherQuotedParameterIsData() {
    RequestTemplate template = new RequestTemplate();
    template.header(
        "Content-Type", "application/x-www-form-urlencoded; note=\"x;charset=ISO-8859-1\"");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "é");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.UTF_8)).isEqualTo("name=%C3%A9");
    assertThat(template.headers().get("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded; charset=UTF-8");
  }

  @Test
  void charsetTextInQuotedParameterBeforeRealCharsetIsData() {
    RequestTemplate template = new RequestTemplate();
    template.header(
        "Content-Type",
        "application/x-www-form-urlencoded; note=\"charset=ISO-8859-1\"; charset=UTF-8");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "café");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.UTF_8)).isEqualTo("name=caf%C3%A9");
    assertThat(template.headers().get("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded; charset=UTF-8");
  }

  @Test
  void escapedQuoteKeepsFakeCharsetInsideQuotedValue() {
    RequestTemplate template = new RequestTemplate();
    template.header(
        "Content-Type",
        "application/x-www-form-urlencoded; note=\"a\\\";charset=ISO-8859-1\"; charset=UTF-8");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "é");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.UTF_8)).isEqualTo("name=%C3%A9");
    assertThat(template.headers().get("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded; charset=UTF-8");
  }

  @Test
  void charsetTextInAnotherParameterNameIsNotTheCharsetParameter() {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", "application/x-www-form-urlencoded; xcharset=ISO-8859-1");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "é");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.UTF_8)).isEqualTo("name=%C3%A9");
    assertThat(template.headers().get("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded; charset=UTF-8");
  }

  @Test
  void charsetParameterWithWhitespaceAroundEqualsIsHonored() {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", "application/x-www-form-urlencoded; charset = ISO-8859-1 ");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "café");

    new FormEncoder().encode(data, Map.class, template);

    assertThat(new String(template.body(), StandardCharsets.ISO_8859_1)).isEqualTo("name=caf%E9");
    assertThat(template.headers().get("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded; charset=ISO-8859-1");
  }
}
