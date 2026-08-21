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

    Encoder encoder =
        MultiEncoder.builder().add(json).add(EncoderPredicate.any(), fallback).build();

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
        MultiEncoder.builder()
            .add(EncoderPredicate.xmlContentType(), xml)
            .add(EncoderPredicate.any(), fallback)
            .build();

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
        MultiEncoder.builder()
            .add(json)
            .add(EncoderPredicate.xmlContentType(), xml)
            .add(EncoderPredicate.bodyType(byte[].class), binary)
            .add(EncoderPredicate.any(), fallback)
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

    Encoder encoder = MultiEncoder.builder().add(json).build();

    encoder.encode("body", String.class, templateWithContentType("application/vnd.github+json"));

    assertThat(json.invoked).isTrue();
  }

  @Test
  void fallsBackToTheEncoderThatAcceptsAnything() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();
    RecordingEncoder fallback = new RecordingEncoder("fallback");

    Encoder encoder =
        MultiEncoder.builder().add(json).add(EncoderPredicate.any(), fallback).build();

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

    Encoder encoder =
        MultiEncoder.builder().add(json).add(EncoderPredicate.any(), fallback).build();

    encoder.encode("body", String.class, templateWithContentType(null));

    assertThat(fallback.invoked).isTrue();
  }

  @Test
  void encodersAreConsultedInOrder() {
    RecordingEncoder first = new RecordingEncoder("first");
    RecordingEncoder second = new RecordingEncoder("second");

    Encoder encoder =
        MultiEncoder.builder()
            .add(EncoderPredicate.jsonContentType(), first)
            .add(EncoderPredicate.jsonContentType(), second)
            .build();

    encoder.encode("body", String.class, templateWithContentType("application/json"));

    assertThat(first.invoked).isTrue();
    assertThat(second.invoked).isFalse();
  }

  @Test
  void pairingReplacesWhatTheEncoderDeclaresAboutItself() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();

    Encoder encoder =
        MultiEncoder.builder().add(PredicatedEncoder.of(EncoderPredicate.any(), json)).build();

    encoder.encode("body", String.class, templateWithContentType("text/plain"));

    assertThat(json.invoked).isTrue();
  }

  @Test
  void narrowingKeepsWhatTheEncoderDeclaresAboutItself() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();
    PredicatedEncoder narrowed =
        PredicatedEncoder.narrowing(
            EncoderPredicate.contentType("application/vnd.acme+json"), json);

    assertThat(
            narrowed.canEncode("body", String.class, templateWithContentType("application/json")))
        .isFalse();
    assertThat(
            narrowed.canEncode(
                "body", String.class, templateWithContentType("application/vnd.acme+json")))
        .isTrue();
    assertThat(narrowed)
        .hasToString(
            "SelfDeclaringJsonEncoder when (Content-Type is application/vnd.acme+json"
                + " and SelfDeclaringJsonEncoder accepts it)");
  }

  @Test
  void narrowingAnEncoderThatDeclaresNothingIsJustThePredicate() {
    RecordingEncoder plain = new RecordingEncoder("plain");
    PredicatedEncoder narrowed =
        PredicatedEncoder.narrowing(EncoderPredicate.jsonContentType(), plain);

    assertThat(narrowed).hasToString("RecordingEncoder when Content-Type is JSON");
    assertThat(
            narrowed.canEncode("body", String.class, templateWithContentType("application/json")))
        .isTrue();
  }

  @Test
  void propagatesEncodeExceptionFromDelegate() {
    Encoder failing =
        (object, bodyType, template) -> {
          throw new EncodeException("boom");
        };

    Encoder encoder =
        MultiEncoder.builder().add(EncoderPredicate.jsonContentType(), failing).build();

    assertThatThrownBy(
            () -> encoder.encode("body", String.class, templateWithContentType("application/json")))
        .isInstanceOf(EncodeException.class)
        .hasMessage("boom");
  }

  @Test
  void throwsWhenNoEncoderAcceptsTheRequest() {
    Encoder encoder =
        MultiEncoder.builder()
            .add(new SelfDeclaringJsonEncoder())
            .add(EncoderPredicate.xmlContentType(), new RecordingEncoder("xml"))
            .build();

    assertThatThrownBy(
            () -> encoder.encode("body", String.class, templateWithContentType("text/plain")))
        .isInstanceOf(EncodeException.class)
        .hasMessage(
            "Unable to encode java.lang.String (Content-Type: text/plain)."
                + " Encoders tried, in order:"
                + "\n  - SelfDeclaringJsonEncoder"
                + "\n  - RecordingEncoder when Content-Type is XML"
                + "\nRegister an encoder that accepts it, or add a catch-all"
                + " (EncoderPredicate.any()) last.");
  }

  @Test
  void theFailureNamesTheRequestWhenTheTemplateHasOne() {
    RequestTemplate template = templateWithContentType("text/plain");
    template.method(Request.HttpMethod.POST);
    template.uri("/orders");

    Encoder encoder = MultiEncoder.builder().add(new SelfDeclaringJsonEncoder()).build();

    assertThatThrownBy(() -> encoder.encode("body", String.class, template))
        .isInstanceOf(EncodeException.class)
        .hasMessageContaining(
            "Unable to encode java.lang.String (Content-Type: text/plain) for POST /orders.");
  }

  @Test
  void theFailureReportsAMissingContentType() {
    Encoder encoder = MultiEncoder.builder().add(new SelfDeclaringJsonEncoder()).build();

    assertThatThrownBy(() -> encoder.encode("body", String.class, templateWithContentType(null)))
        .isInstanceOf(EncodeException.class)
        .hasMessageContaining("(Content-Type: not set)");
  }

  @Test
  void throwsWhenNoEncodersAreConfigured() {
    assertThatThrownBy(() -> MultiEncoder.builder().build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("at least one encoder is required");
  }

  @Test
  void rejectsNullArguments() {
    assertThatThrownBy(() -> MultiEncoder.builder().add(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("encoder cannot be null");
    assertThatThrownBy(() -> MultiEncoder.builder().add(null, new DefaultEncoder()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("predicate cannot be null");
    assertThatThrownBy(() -> MultiEncoder.builder().add(EncoderPredicate.any(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("encoder cannot be null");
  }

  @Test
  void aMultiEncoderNestsInsideAnother() {
    RecordingEncoder xml = new RecordingEncoder("xml");
    PredicatedEncoder contributed =
        MultiEncoder.builder()
            .add(new SelfDeclaringJsonEncoder())
            .add(EncoderPredicate.xmlContentType(), xml)
            .build();

    Encoder encoder =
        MultiEncoder.builder()
            .add(contributed)
            .add(EncoderPredicate.any(), new RecordingEncoder("fallback"))
            .build();

    RequestTemplate template = templateWithContentType("application/xml");
    encoder.encode("body", String.class, template);

    assertThat(xml.invoked).isTrue();
  }

  @Test
  void aNestedMultiEncoderAcceptsWhateverItsEncodersAccept() {
    PredicatedEncoder contributed =
        MultiEncoder.builder().add(new SelfDeclaringJsonEncoder()).build();

    assertThat(
            contributed.canEncode(
                "body", String.class, templateWithContentType("application/json")))
        .isTrue();
    assertThat(contributed.canEncode("body", String.class, templateWithContentType("text/plain")))
        .isFalse();
  }

  @Test
  void theFailureUnfoldsNestedEncoders() {
    PredicatedEncoder contributed =
        MultiEncoder.builder()
            .add(new SelfDeclaringJsonEncoder())
            .add(EncoderPredicate.xmlContentType(), new RecordingEncoder("xml"))
            .build();

    Encoder encoder =
        MultiEncoder.builder().add(new SelfDeclaringJsonEncoder()).add(contributed).build();

    assertThatThrownBy(
            () -> encoder.encode("body", String.class, templateWithContentType("text/plain")))
        .isInstanceOf(EncodeException.class)
        .hasMessageContaining(
            "Encoders tried, in order:"
                + "\n  - SelfDeclaringJsonEncoder"
                + "\n  - MultiEncoder:"
                + "\n    - SelfDeclaringJsonEncoder"
                + "\n    - RecordingEncoder when Content-Type is XML");
  }

  @Test
  void theFailureReportsTheAcceptHeaderWhenThereIsOne() {
    RequestTemplate template = templateWithContentType("text/plain");
    template.header("Accept", "application/json");

    Encoder encoder = MultiEncoder.builder().add(new SelfDeclaringJsonEncoder()).build();

    assertThatThrownBy(() -> encoder.encode("body", String.class, template))
        .isInstanceOf(EncodeException.class)
        .hasMessageContaining("(Content-Type: text/plain, Accept: application/json)");
  }

  @Test
  void narrowKeepsTheEncodersOwnDeclaration() {
    SelfDeclaringJsonEncoder json = new SelfDeclaringJsonEncoder();
    Encoder encoder =
        MultiEncoder.builder()
            .narrow(EncoderPredicate.bodyType(byte[].class), json)
            .add(EncoderPredicate.any(), new RecordingEncoder("fallback"))
            .build();

    encoder.encode("body", String.class, templateWithContentType("application/json"));
    assertThat(json.invoked).isFalse();

    encoder.encode(new byte[0], byte[].class, templateWithContentType("application/json"));
    assertThat(json.invoked).isTrue();
  }

  @Test
  void toStringDescribesEncoders() {
    Encoder encoder =
        MultiEncoder.builder()
            .add(new SelfDeclaringJsonEncoder())
            .add(EncoderPredicate.jsonContentType(), new RecordingEncoder("json"))
            .build();

    assertThat(encoder.toString())
        .isEqualTo(
            "MultiEncoder[SelfDeclaringJsonEncoder, RecordingEncoder when Content-Type is JSON]");
  }
}
