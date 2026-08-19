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
package feign.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Request;
import feign.RequestTemplate;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class MultiEncoderTest {

  private static class RecordingEncoder implements Encoder {
    private final String body;
    boolean invoked;

    RecordingEncoder(String body) {
      this.body = body;
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
      invoked = true;
      template.body(Request.Body.create(body));
    }
  }

  private static RequestTemplate templateWithContentType(String contentType) {
    RequestTemplate template = new RequestTemplate();
    if (contentType != null) {
      template.header("Content-Type", contentType);
    }
    return template;
  }

  @Test
  void usesFirstDelegateWhosePredicateAccepts() {
    RecordingEncoder json = new RecordingEncoder("json");
    RecordingEncoder xml = new RecordingEncoder("xml");
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder =
        MultiEncoder.of(
            fallback,
            PredicatedEncoder.forJsonContentType(json),
            PredicatedEncoder.forXmlContentType(xml));

    RequestTemplate template = templateWithContentType("application/json");
    encoder.encode("body", String.class, template);

    assertThat(json.invoked).isTrue();
    assertThat(xml.invoked).isFalse();
    assertThat(fallback.invoked).isFalse();
    assertThat(template.requestBody().asString()).isEqualTo("json");
  }

  @Test
  void matchesSuffixedContentTypes() {
    RecordingEncoder json = new RecordingEncoder("json");
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.of(fallback, PredicatedEncoder.forJsonContentType(json));

    encoder.encode("body", String.class, templateWithContentType("application/vnd.github+json"));

    assertThat(json.invoked).isTrue();
    assertThat(fallback.invoked).isFalse();
  }

  @Test
  void fallsBackToDefaultEncoderWhenNoPredicateAccepts() {
    RecordingEncoder json = new RecordingEncoder("json");
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.of(fallback, PredicatedEncoder.forJsonContentType(json));

    RequestTemplate template = templateWithContentType("text/plain");
    encoder.encode("body", String.class, template);

    assertThat(json.invoked).isFalse();
    assertThat(fallback.invoked).isTrue();
    assertThat(template.requestBody().asString()).isEqualTo("fallback");
  }

  @Test
  void fallsBackToDefaultEncoderWhenNoContentTypeIsSet() {
    RecordingEncoder json = new RecordingEncoder("json");
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.of(fallback, PredicatedEncoder.forJsonContentType(json));

    encoder.encode("body", String.class, templateWithContentType(null));

    assertThat(json.invoked).isFalse();
    assertThat(fallback.invoked).isTrue();
  }

  @Test
  void withoutDelegatesEverythingGoesToTheDefaultEncoder() {
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.of(fallback);

    encoder.encode("body", String.class, templateWithContentType("application/json"));

    assertThat(fallback.invoked).isTrue();
  }

  @Test
  void propagatesEncodeExceptionFromDelegate() {
    Encoder failing =
        (object, bodyType, template) -> {
          throw new EncodeException("boom");
        };

    Encoder encoder =
        MultiEncoder.of(new DefaultEncoder(), PredicatedEncoder.forJsonContentType(failing));

    assertThatThrownBy(
            () -> encoder.encode("body", String.class, templateWithContentType("application/json")))
        .isInstanceOf(EncodeException.class)
        .hasMessage("boom");
  }

  @Test
  void rejectsNullDefaultEncoder() {
    assertThatThrownBy(() -> MultiEncoder.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("defaultEncoder cannot be null");
  }

  @Test
  void rejectsNullDelegate() {
    assertThatThrownBy(() -> MultiEncoder.of(new DefaultEncoder(), Collections.singletonList(null)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("delegates cannot contain null");
  }

  @Test
  void toStringDescribesDelegates() {
    Encoder encoder =
        MultiEncoder.of(
            new DefaultEncoder(), PredicatedEncoder.forJsonContentType(new DefaultEncoder()));

    assertThat(encoder.toString()).startsWith("MultiEncoder{defaultEncoder=");
    assertThat(encoder.toString()).contains("PredicatedEncoder{");
  }

  @Test
  void listFactoryIsEquivalentToVarargs() {
    RecordingEncoder json = new RecordingEncoder("json");
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder =
        MultiEncoder.of(fallback, Arrays.asList(PredicatedEncoder.forJsonContentType(json)));

    encoder.encode("body", String.class, templateWithContentType("application/json"));

    assertThat(json.invoked).isTrue();
  }
}
