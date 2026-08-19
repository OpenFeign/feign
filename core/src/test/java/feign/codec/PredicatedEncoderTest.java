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
import org.junit.jupiter.api.Test;

class PredicatedEncoderTest {

  private static class RecordingEncoder implements Encoder {
    boolean invoked;

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
      invoked = true;
      template.body(Request.Body.create("encoded"));
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
  void delegatesWhenPredicateAccepts() {
    RecordingEncoder delegate = new RecordingEncoder();
    PredicatedEncoder encoder = new PredicatedEncoder((o, t, tpl) -> true, delegate);

    RequestTemplate template = templateWithContentType(null);
    encoder.encode("body", String.class, template);

    assertThat(delegate.invoked).isTrue();
    assertThat(template.requestBody().asString()).isEqualTo("encoded");
  }

  @Test
  void throwsAndSkipsDelegateWhenPredicateRejects() {
    RecordingEncoder delegate = new RecordingEncoder();
    PredicatedEncoder encoder = new PredicatedEncoder((o, t, tpl) -> false, delegate);

    assertThatThrownBy(() -> encoder.encode("body", String.class, templateWithContentType(null)))
        .isInstanceOf(EncodeException.class);

    assertThat(delegate.invoked).isFalse();
  }

  @Test
  void canEncodeReflectsThePredicate() {
    PredicatedEncoder encoder =
        new PredicatedEncoder((o, t, tpl) -> "yes".equals(o), new RecordingEncoder());

    assertThat(encoder.canEncode("yes", String.class, templateWithContentType(null))).isTrue();
    assertThat(encoder.canEncode("no", String.class, templateWithContentType(null))).isFalse();
  }

  @Test
  void forJsonContentTypeMatchesJsonOnly() {
    PredicatedEncoder encoder = PredicatedEncoder.forJsonContentType(new RecordingEncoder());

    assertThat(encoder.canEncode(null, String.class, templateWithContentType("application/json")))
        .isTrue();
    assertThat(
            encoder.canEncode(
                null, String.class, templateWithContentType("application/json;charset=utf-8")))
        .isTrue();
    assertThat(
            encoder.canEncode(
                null, String.class, templateWithContentType("application/vnd.github+json")))
        .isTrue();
    assertThat(encoder.canEncode(null, String.class, templateWithContentType("application/xml")))
        .isFalse();
    assertThat(encoder.canEncode(null, String.class, templateWithContentType(null))).isFalse();
  }

  @Test
  void forXmlContentTypeMatchesXmlOnly() {
    PredicatedEncoder encoder = PredicatedEncoder.forXmlContentType(new RecordingEncoder());

    assertThat(encoder.canEncode(null, String.class, templateWithContentType("application/xml")))
        .isTrue();
    assertThat(encoder.canEncode(null, String.class, templateWithContentType("text/xml"))).isTrue();
    assertThat(
            encoder.canEncode(null, String.class, templateWithContentType("application/soap+xml")))
        .isTrue();
    assertThat(encoder.canEncode(null, String.class, templateWithContentType("application/json")))
        .isFalse();
  }

  @Test
  void contentTypeHeaderNameIsMatchedCaseInsensitively() {
    PredicatedEncoder encoder = PredicatedEncoder.forJsonContentType(new RecordingEncoder());

    RequestTemplate template = new RequestTemplate();
    template.header("content-type", "application/json");

    assertThat(encoder.canEncode(null, String.class, template)).isTrue();
  }

  @Test
  void forEmptyBodyMatchesNullBodyOnly() {
    PredicatedEncoder encoder = PredicatedEncoder.forEmptyBody(new RecordingEncoder());

    assertThat(encoder.canEncode(null, String.class, templateWithContentType(null))).isTrue();
    assertThat(encoder.canEncode("body", String.class, templateWithContentType(null))).isFalse();
  }

  @Test
  void rejectsNullConstructorArguments() {
    assertThatThrownBy(() -> new PredicatedEncoder(null, new RecordingEncoder()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("predicate cannot be null");
    assertThatThrownBy(() -> new PredicatedEncoder((o, t, tpl) -> true, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("delegate cannot be null");
  }
}
