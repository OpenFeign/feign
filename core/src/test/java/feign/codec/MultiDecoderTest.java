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

    Decoder decoder = MultiDecoder.builder(fallback).add(json).build();

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
        MultiDecoder.builder(fallback).add(DecoderPredicate.xmlContentType(), xml).build();

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
        MultiDecoder.builder(fallback)
            .add(json)
            .add(DecoderPredicate.xmlContentType(), xml)
            .add(DecoderPredicate.contentType("text/csv"), csv)
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
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder = MultiDecoder.builder(fallback).add(json).build();

    assertThat(decoder.decode(responseWithContentType("application/vnd.github+json"), String.class))
        .isEqualTo("json");
  }

  @Test
  void fallsBackToTheDefaultDecoderWhenNoDelegateAccepts() throws IOException {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder = MultiDecoder.builder(fallback).add(json).build();

    assertThat(decoder.decode(responseWithContentType("text/plain"), String.class))
        .isEqualTo("fallback");
    assertThat(json.invoked).isFalse();
  }

  @Test
  void fallsBackWhenTheResponseCarriesNoContentType() throws IOException {
    SelfDeclaringJsonDecoder json = new SelfDeclaringJsonDecoder();
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder = MultiDecoder.builder(fallback).add(json).build();

    assertThat(decoder.decode(responseWithContentType(null), String.class)).isEqualTo("fallback");
  }

  @Test
  void consultsDelegatesInTheOrderTheyWereAdded() throws IOException {
    RecordingDecoder first = new RecordingDecoder("first");
    RecordingDecoder second = new RecordingDecoder("second");
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder =
        MultiDecoder.builder(fallback)
            .add(DecoderPredicate.jsonContentType(), first)
            .add(DecoderPredicate.jsonContentType(), second)
            .build();

    assertThat(decoder.decode(responseWithContentType("application/json"), String.class))
        .isEqualTo("first");
    assertThat(second.invoked).isFalse();
  }

  @Test
  void aBareLambdaIsADecoderThatAcceptsEverything() throws IOException {
    PredicatedDecoder anything = (response, type) -> "anything";
    RecordingDecoder fallback = new RecordingDecoder("fallback");

    Decoder decoder = MultiDecoder.builder(fallback).add(anything).build();

    assertThat(decoder.decode(responseWithContentType("text/plain"), String.class))
        .isEqualTo("anything");
    assertThat(fallback.invoked).isFalse();
  }

  @Test
  void propagatesIoExceptionsFromTheSelectedDecoder() {
    Decoder failing =
        (response, type) -> {
          throw new IOException("boom");
        };

    Decoder decoder =
        MultiDecoder.builder(new RecordingDecoder("fallback"))
            .add(DecoderPredicate.jsonContentType(), failing)
            .build();

    assertThatThrownBy(
            () -> decoder.decode(responseWithContentType("application/json"), String.class))
        .isInstanceOf(IOException.class)
        .hasMessage("boom");
  }

  @Test
  void rejectsANullDefaultDecoder() {
    assertThatThrownBy(() -> MultiDecoder.builder(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("defaultDecoder cannot be null");
  }

  @Test
  void rejectsNullDelegates() {
    MultiDecoder.Builder builder = MultiDecoder.builder(new RecordingDecoder("fallback"));

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
  void describesItsDelegates() {
    Decoder decoder =
        MultiDecoder.builder(
                new RecordingDecoder("fallback") {
                  @Override
                  public String toString() {
                    return "fallback";
                  }
                })
            .add(
                DecoderPredicate.jsonContentType(),
                new RecordingDecoder("json") {
                  @Override
                  public String toString() {
                    return "json";
                  }
                })
            .build();

    assertThat(decoder.toString())
        .isEqualTo("MultiDecoder{defaultDecoder=fallback, delegates=[json]}");
  }
}
