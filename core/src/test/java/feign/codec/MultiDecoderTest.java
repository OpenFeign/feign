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
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiDecoderTest {

  /** A plain decoder, with no opinion about what it can handle. */
  private static class RecordingDecoder implements Decoder {
    private final String result;
    boolean invoked;

    RecordingDecoder(String result) {
      this.result = result;
    }

    @Override
    public Object decode(Response response, Type type) {
      invoked = true;
      return result;
    }
  }

  /** A decoder that declares its own applicability, the way feign-gson and friends now do. */
  private static class SelfDeclaringJsonDecoder extends RecordingDecoder
      implements PredicatedDecoder {

    SelfDeclaringJsonDecoder() {
      super("json");
    }

    @Override
    public boolean canDecode(Response response, Type type) {
      return Util.isJsonContentType(response);
    }
  }

  private static Response responseWithContentType(String contentType) {
    return responseWithContentType(contentType, 200, "body");
  }

  private static Response responseWithContentType(String contentType, int status, String body) {
    Map<String, Collection<String>> headers = new HashMap<>();
    if (contentType != null) {
      headers.put("Content-Type", Collections.singletonList(contentType));
    }
    Response.Builder builder =
        Response.builder()
            .status(status)
            .reason("OK")
            .headers(headers)
            .request(
                Request.create(
                    HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8, null));
    if (body != null) {
      builder.body(body, Util.UTF_8);
    }
    return builder.build();
  }

  @Test
  void routesToTheDecoderThatDeclaresItCanHandleTheResponse() throws IOException {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder =
        MultiDecoder.builder().add(json).add(DecoderPredicate.any(), fallback).build();

    assertThat(decoder.decode(responseWithContentType("application/json"), String.class))
        .isEqualTo("json");
    assertThat(json.invoked).isTrue();
    assertThat(fallback.invoked).isFalse();
  }

  @Test
  void pairsAPredicateWithADecoderThatDoesNotDeclareItself() throws IOException {
    RecordingDecoder xml = new RecordingDecoder("xml");
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder =
        MultiDecoder.builder()
            .add(DecoderPredicate.xmlContentType(), xml)
            .add(DecoderPredicate.any(), fallback)
            .build();

    assertThat(decoder.decode(responseWithContentType("application/xml"), String.class))
        .isEqualTo("xml");
    assertThat(fallback.invoked).isFalse();
  }

  @Test
  void mixesSelfDeclaringDecodersAndPairs() throws IOException {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();
    RecordingDecoder xml = new RecordingDecoder("xml");
    RecordingDecoder csv = new RecordingDecoder("csv");
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder =
        MultiDecoder.builder()
            .add(json)
            .add(DecoderPredicate.xmlContentType(), xml)
            .add(DecoderPredicate.contentType("text/csv"), csv)
            .add(DecoderPredicate.any(), fallback)
            .build();

    assertThat(decoder.decode(responseWithContentType("text/csv;charset=utf-8"), String.class))
        .isEqualTo("csv");
    assertThat(json.invoked).isFalse();
    assertThat(xml.invoked).isFalse();
    assertThat(fallback.invoked).isFalse();
  }

  @Test
  void matchesSuffixedContentTypes() throws IOException {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();

    Decoder decoder = MultiDecoder.builder().add(json).build();

    assertThat(decoder.decode(responseWithContentType("application/vnd.github+json"), String.class))
        .isEqualTo("json");
  }

  @Test
  void fallsBackToTheDecoderThatAcceptsAnything() throws IOException {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder =
        MultiDecoder.builder().add(json).add(DecoderPredicate.any(), fallback).build();

    assertThat(decoder.decode(responseWithContentType("text/plain"), String.class))
        .isEqualTo("fallback");
    assertThat(json.invoked).isFalse();
  }

  @Test
  void fallsBackWhenTheResponseCarriesNoContentType() throws IOException {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder =
        MultiDecoder.builder().add(json).add(DecoderPredicate.any(), fallback).build();

    assertThat(decoder.decode(responseWithContentType(null), String.class)).isEqualTo("fallback");
  }

  @Test
  void consultsDecodersInTheOrderTheyWereAdded() throws IOException {
    RecordingDecoder first = new RecordingDecoder("first");
    RecordingDecoder second = new RecordingDecoder("second");

    Decoder decoder =
        MultiDecoder.builder()
            .add(DecoderPredicate.jsonContentType(), first)
            .add(DecoderPredicate.jsonContentType(), second)
            .build();

    assertThat(decoder.decode(responseWithContentType("application/json"), String.class))
        .isEqualTo("first");
    assertThat(second.invoked).isFalse();
  }

  @Test
  void pairingReplacesWhatTheDecoderDeclaresAboutItself() throws IOException {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();

    Decoder decoder =
        MultiDecoder.builder().add(PredicatedDecoder.of(DecoderPredicate.any(), json)).build();

    assertThat(decoder.decode(responseWithContentType("text/plain"), String.class))
        .isEqualTo("json");
  }

  @Test
  void narrowingKeepsWhatTheDecoderDeclaresAboutItself() {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();
    PredicatedDecoder narrowed = PredicatedDecoder.narrowing(DecoderPredicate.status(200), json);

    assertThat(narrowed.canDecode(responseWithContentType("application/json"), String.class))
        .isTrue();
    assertThat(
            narrowed.canDecode(
                responseWithContentType("application/json", 204, null), String.class))
        .isFalse();
    assertThat(narrowed.canDecode(responseWithContentType("text/plain"), String.class)).isFalse();
    assertThat(narrowed)
        .hasToString(
            "SelfDeclaringJsonDecoder when (status is one of [200]"
                + " and SelfDeclaringJsonDecoder accepts it)");
  }

  @Test
  void narrowingADecoderThatDeclaresNothingIsJustThePredicate() {
    RecordingDecoder plain = new RecordingDecoder("plain");
    PredicatedDecoder narrowed =
        PredicatedDecoder.narrowing(DecoderPredicate.jsonContentType(), plain);

    assertThat(narrowed).hasToString("RecordingDecoder when Content-Type is JSON");
    assertThat(narrowed.canDecode(responseWithContentType("application/json"), String.class))
        .isTrue();
  }

  @Test
  void throwsWhenNoDecoderAcceptsTheResponse() {
    Decoder decoder =
        MultiDecoder.builder()
            .add(new SelfDeclaringJsonDecoder())
            .add(DecoderPredicate.xmlContentType(), new RecordingDecoder("xml"))
            .build();

    assertThatThrownBy(() -> decoder.decode(responseWithContentType("text/plain"), String.class))
        .isInstanceOf(DecodeException.class)
        .hasMessage(
            "Unable to decode 200 response (Content-Type: text/plain) as java.lang.String."
                + " Decoders tried, in order:"
                + "\n  - SelfDeclaringJsonDecoder"
                + "\n  - RecordingDecoder when Content-Type is XML"
                + "\nAdd a decoder guarded by DecoderPredicate.any() last to act as a default.");
  }

  @Test
  void theFailureReportsAMissingContentType() {
    Decoder decoder = MultiDecoder.builder().add(new SelfDeclaringJsonDecoder()).build();

    assertThatThrownBy(() -> decoder.decode(responseWithContentType(null), String.class))
        .isInstanceOf(DecodeException.class)
        .hasMessageContaining("(Content-Type: not set)");
  }

  @Test
  void throwsWhenNoDecodersAreConfigured() {
    Decoder decoder = MultiDecoder.builder().build();

    assertThatThrownBy(
            () -> decoder.decode(responseWithContentType("application/json"), String.class))
        .isInstanceOf(DecodeException.class)
        .hasMessage(
            "Unable to decode 200 response (Content-Type: application/json) as java.lang.String."
                + " No decoders were configured.");
  }

  @Test
  void propagatesIoExceptionsFromTheSelectedDecoder() {
    Decoder failing =
        (response, type) -> {
          throw new IOException("boom");
        };

    Decoder decoder =
        MultiDecoder.builder().add(DecoderPredicate.jsonContentType(), failing).build();

    assertThatThrownBy(
            () -> decoder.decode(responseWithContentType("application/json"), String.class))
        .isInstanceOf(IOException.class)
        .hasMessage("boom");
  }

  @Test
  void rejectsNullDecoders() {
    MultiDecoder.Builder builder = MultiDecoder.builder();

    assertThatThrownBy(() -> builder.add((PredicatedDecoder) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("decoder cannot be null");
    assertThatThrownBy(() -> builder.add(null, new RecordingDecoder("x")))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("predicate cannot be null");
    assertThatThrownBy(() -> builder.add(DecoderPredicate.jsonContentType(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("decoder cannot be null");
  }

  @Test
  void describesItsDecoders() {
    Decoder decoder =
        MultiDecoder.builder()
            .add(new SelfDeclaringJsonDecoder())
            .add(DecoderPredicate.jsonContentType(), new RecordingDecoder("json"))
            .build();

    assertThat(decoder.toString())
        .isEqualTo(
            "MultiDecoder[SelfDeclaringJsonDecoder, RecordingDecoder when Content-Type is JSON]");
  }
}
