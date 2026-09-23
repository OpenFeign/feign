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
package feign.core.codec;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import feign.Feign;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestLine;
import feign.Response;
import feign.TypedResponse;
import feign.Util;
import feign.codec.DecodeException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.internal.BufferMockResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

@SuppressWarnings("deprecation")
class DefaultDecoderTest {

  private final DefaultDecoder decoder = new DefaultDecoder();

  @Test
  void declaresTheTypesItDecodes() throws Exception {
    assertThat(decoder.canDecode(knownResponse(), String.class)).isTrue();
    assertThat(decoder.canDecode(knownResponse(), byte[].class)).isTrue();
    assertThat(decoder.canDecode(knownResponse(), Document.class)).isFalse();
    assertThat(decoder.canDecode(nullBodyResponse(), Document.class)).isTrue();
  }

  @Test
  void decodesToString() throws Exception {
    Response response = knownResponse();
    Object decodedObject = decoder.decode(response, String.class);
    assertThat(decodedObject.getClass()).isEqualTo(String.class);
    assertThat(decodedObject).hasToString("response body");
  }

  @Test
  void decodesToStringHonouringContentTypeCharset() throws Exception {
    String content = "él pingüino";
    byte[] body = content.getBytes(StandardCharsets.ISO_8859_1);
    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singleton("text/plain; charset=ISO-8859-1"));
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .headers(headers)
            .request(
                Request.create(
                    HttpMethod.GET, "/api", Collections.emptyMap(), (Request.Body) null, null))
            .body(new ByteArrayInputStream(body), body.length)
            .build();
    Object decodedObject = decoder.decode(response, String.class);
    assertThat(decodedObject).isEqualTo(content);
  }

  @Test
  void decodesToByteArray() throws Exception {
    Response response = knownResponse();
    Object decodedObject = decoder.decode(response, byte[].class);
    assertThat(decodedObject.getClass()).isEqualTo(byte[].class);
    assertThat(new String((byte[]) decodedObject, UTF_8)).isEqualTo("response body");
  }

  @Test
  void decodesNullBodyToNull() throws Exception {
    assertThat(decoder.decode(nullBodyResponse(), Document.class)).isNull();
  }

  @Test
  void refusesToDecodeOtherTypes() throws Exception {
    Throwable exception =
        assertThatExceptionOfType(DecodeException.class)
            .isThrownBy(() -> decoder.decode(knownResponse(), Document.class))
            .actual();
    assertThat(exception.getMessage()).contains(" is not a type supported by this decoder.");
  }

  private Response knownResponse() {
    String content = "response body";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(UTF_8));
    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singleton("text/plain"));
    return Response.builder()
        .status(200)
        .reason("OK")
        .headers(headers)
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, null))
        .body(inputStream, content.length())
        .build();
  }

  private Response nullBodyResponse() {
    return Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.<String, Collection<String>>emptyMap())
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, null))
        .build();
  }

  interface LargeStreamTestInterface {

    @RequestLine("GET /")
    InputStream getLargeStream();

    @RequestLine("GET /")
    Reader getLargeReader();

    @RequestLine("GET /")
    TypedResponse<InputStream> getLargeStreamTypedResponse();
  }

  @Test
  void streamingResponse() throws Exception {

    try (MockWebServer server = new MockWebServer()) {

      server.start();

      byte[] expectedResponse = new byte[16184];
      new Random().nextBytes(expectedResponse);
      server.enqueue(
          new MockResponse.Builder()
              .body(new BufferMockResponseBody(new Buffer().write(expectedResponse)))
              .build());

      LargeStreamTestInterface api =
          Feign.builder()
              .target(LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

      try (InputStream is = api.getLargeStream()) {
        byte[] out = is.readAllBytes();
        assertThat(out.length).isEqualTo(expectedResponse.length);
        assertThat(out).isEqualTo(expectedResponse);
      }
    }
  }

  @Test
  void streamingReaderResponse() throws Exception {
    try (MockWebServer server = new MockWebServer()) {

      server.start();

      String expectedResponse =
          new Random()
              .ints(1, 1500 + 1)
              .limit(16184)
              .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
              .toString();

      server.enqueue(
          new MockResponse.Builder()
              .body(
                  new BufferMockResponseBody(
                      new Buffer().write(expectedResponse.getBytes(StandardCharsets.UTF_16))))
              .addHeader("content-type", "text/plan; charset=utf-16")
              .build());

      LargeStreamTestInterface api =
          Feign.builder()
              .target(LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

      try (Reader r = api.getLargeReader()) {
        String out = Util.toString(r);
        assertThat(out.length()).isEqualTo(expectedResponse.length());
        assertThat(out).isEqualTo(expectedResponse);
      }
    }
  }

  @Test
  void streamingReaderResponseWithNoCharset() throws Exception {

    try (MockWebServer server = new MockWebServer()) {

      server.start();

      String expectedResponse =
          new Random()
              .ints(1, 1500 + 1)
              .limit(16184)
              .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
              .toString();

      server.enqueue(
          new MockResponse.Builder()
              .body(
                  new BufferMockResponseBody(
                      new Buffer().write(expectedResponse.getBytes(Util.UTF_8))))
              .addHeader("content-type", "text/plan")
              .build());

      LargeStreamTestInterface api =
          Feign.builder()
              .target(LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

      try (Reader r = api.getLargeReader()) {
        String out = Util.toString(r);
        assertThat(out.length()).isEqualTo(expectedResponse.length());
        assertThat(out).isEqualTo(expectedResponse);
      }
    }
  }

  @Test
  void streamingTypedResponse() throws Exception {

    try (MockWebServer server = new MockWebServer()) {

      server.start();

      byte[] expectedResponse = new byte[16184];
      new Random().nextBytes(expectedResponse);
      server.enqueue(
          new MockResponse.Builder()
              .body(new BufferMockResponseBody(new Buffer().write(expectedResponse)))
              .build());

      LargeStreamTestInterface api =
          Feign.builder()
              .target(LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

      TypedResponse<InputStream> resp = api.getLargeStreamTypedResponse();
      try {
        byte[] out = resp.body().readAllBytes();
        assertThat(out.length).isEqualTo(expectedResponse.length);
        assertThat(out).isEqualTo(expectedResponse);
      } finally {
        resp.body().close();
      }
    }
  }
}
