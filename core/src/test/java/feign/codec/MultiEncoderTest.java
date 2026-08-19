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
import feign.Util;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;

class MultiEncoderTest {

  /** A plain encoder, with no opinion about what it can handle. */
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

  /** An encoder that declares its own applicability, the way feign-gson and friends now do. */
  private static class SelfDeclaringJsonEncoder extends RecordingEncoder
      implements PredicatedEncoder {

    SelfDeclaringJsonEncoder() {
      super("json");
    }

    @Override
    public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
      return Util.isJsonContentType(template);
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
  void routesToTheEncoderThatDeclaresItCanHandleTheRequest() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.builder(fallback).add(json).build();

    RequestTemplate template = templateWithContentType("application/json");
    encoder.encode("body", String.class, template);

    assertThat(json.invoked).isTrue();
    assertThat(fallback.invoked).isFalse();
    assertThat(template.requestBody().asString()).isEqualTo("json");
  }

  @Test
  void pairsAPredicateWithAnEncoderThatDoesNotDeclareItself() {
    RecordingEncoder xml = new RecordingEncoder("xml");
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder =
        MultiEncoder.builder(fallback).add(EncoderPredicate.xmlContentType(), xml).build();

    encoder.encode("body", String.class, templateWithContentType("application/xml"));

    assertThat(xml.invoked).isTrue();
    assertThat(fallback.invoked).isFalse();
  }

  @Test
  void mixesSelfDeclaringEncodersAndPairs() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();
    RecordingEncoder xml = new RecordingEncoder("xml");
    RecordingEncoder binary = new RecordingEncoder("binary");
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder =
        MultiEncoder.builder(fallback)
            .add(json)
            .add(EncoderPredicate.xmlContentType(), xml)
            .add(EncoderPredicate.bodyType(byte[].class), binary)
            .build();

    encoder.encode(
        new byte[] {1}, byte[].class, templateWithContentType("application/octet-stream"));

    assertThat(binary.invoked).isTrue();
    assertThat(json.invoked).isFalse();
    assertThat(xml.invoked).isFalse();
    assertThat(fallback.invoked).isFalse();
  }

  @Test
  void matchesSuffixedContentTypes() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.builder(fallback).add(json).build();

    encoder.encode("body", String.class, templateWithContentType("application/vnd.github+json"));

    assertThat(json.invoked).isTrue();
  }

  @Test
  void fallsBackWhenNoDelegateAccepts() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.builder(fallback).add(json).build();

    RequestTemplate template = templateWithContentType("text/plain");
    encoder.encode("body", String.class, template);

    assertThat(json.invoked).isFalse();
    assertThat(fallback.invoked).isTrue();
    assertThat(template.requestBody().asString()).isEqualTo("fallback");
  }

  @Test
  void fallsBackWhenNoContentTypeIsSet() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.builder(fallback).add(json).build();

    encoder.encode("body", String.class, templateWithContentType(null));

    assertThat(fallback.invoked).isTrue();
  }

  @Test
  void withNoDelegatesEverythingGoesToTheDefaultEncoder() {
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder = MultiEncoder.builder(fallback).build();

    encoder.encode("body", String.class, templateWithContentType("application/json"));

    assertThat(fallback.invoked).isTrue();
  }

  @Test
  void delegatesAreConsultedInOrder() {
    RecordingEncoder first = new RecordingEncoder("first");
    RecordingEncoder second = new RecordingEncoder("second");
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder =
        MultiEncoder.builder(fallback)
            .add(EncoderPredicate.jsonContentType(), first)
            .add(EncoderPredicate.jsonContentType(), second)
            .build();

    encoder.encode("body", String.class, templateWithContentType("application/json"));

    assertThat(first.invoked).isTrue();
    assertThat(second.invoked).isFalse();
  }

  @Test
  void anEncoderWithoutAPredicateAcceptsEverything() {
    // a bare lambda is a PredicatedEncoder whose default canEncode returns true
    RecordingEncoder fallback = new RecordingEncoder("fallback");
    PredicatedEncoder greedy = (object, bodyType, template) -> template.body("greedy");

    Encoder encoder = MultiEncoder.builder(fallback).add(greedy).build();

    RequestTemplate template = templateWithContentType("text/plain");
    encoder.encode("body", String.class, template);

    assertThat(fallback.invoked).isFalse();
    assertThat(template.requestBody().asString()).isEqualTo("greedy");
  }

  @Test
  void propagatesEncodeExceptionFromDelegate() {
    Encoder failing =
        (object, bodyType, template) -> {
          throw new EncodeException("boom");
        };

    Encoder encoder =
        MultiEncoder.builder(new DefaultEncoder())
            .add(EncoderPredicate.jsonContentType(), failing)
            .build();

    assertThatThrownBy(
            () -> encoder.encode("body", String.class, templateWithContentType("application/json")))
        .isInstanceOf(EncodeException.class)
        .hasMessage("boom");
  }

  @Test
  void rejectsNullArguments() {
    assertThatThrownBy(() -> MultiEncoder.builder(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("defaultEncoder cannot be null");
    assertThatThrownBy(() -> MultiEncoder.builder(new DefaultEncoder()).add(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("encoder cannot be null");
    assertThatThrownBy(
            () -> MultiEncoder.builder(new DefaultEncoder()).add(null, new DefaultEncoder()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("predicate cannot be null");
  }

  @Test
  void toStringDescribesDelegates() {
    Encoder encoder =
        MultiEncoder.builder(new DefaultEncoder())
            .add(EncoderPredicate.jsonContentType(), new RecordingEncoder("json"))
            .build();

    assertThat(encoder.toString()).startsWith("MultiEncoder{defaultEncoder=");
  }
}
