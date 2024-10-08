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
package feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeignExceptionTest {

  @Test
  void canCreateWithRequestAndResponse() {
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/home",
            Collections.emptyMap(),
            "data".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
            null);

    Response response =
        Response.builder()
            .status(400)
            .body("response".getBytes(StandardCharsets.UTF_8))
            .request(request)
            .build();

    FeignException exception =
        FeignException.errorReading(request, response, new IOException("socket closed"));
    assertThat(exception.responseBody()).isNotEmpty();
    assertThat(exception.hasRequest()).isTrue();
    assertThat(exception.request()).isNotNull();
  }

  @Test
  void canCreateWithRequestOnly() {
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/home",
            Collections.emptyMap(),
            "data".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
            null);

    FeignException exception =
        FeignException.errorExecuting(request, new IOException("connection timeout"));
    assertThat(exception.responseBody()).isEmpty();
    assertThat(exception.content()).isNullOrEmpty();
    assertThat(exception.hasRequest()).isTrue();
    assertThat(exception.request()).isNotNull();
  }

  @Test
  void createFeignExceptionWithCorrectCharsetResponse() {
    Map<String, Collection<String>> map = new HashMap<>();
    map.put("connection", new ArrayList<>(Collections.singletonList("keep-alive")));
    map.put("content-length", new ArrayList<>(Collections.singletonList("100")));
    map.put(
        "content-type",
        new ArrayList<>(Collections.singletonList("application/json;charset=UTF-16BE")));

    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/home",
            Collections.emptyMap(),
            "data".getBytes(StandardCharsets.UTF_16BE),
            StandardCharsets.UTF_16BE,
            null);

    Response response =
        Response.builder()
            .status(400)
            .body("response".getBytes(StandardCharsets.UTF_16BE))
            .headers(map)
            .request(request)
            .build();

    FeignException exception = FeignException.errorStatus("methodKey", response);
    assertThat(exception.getMessage())
        .isEqualTo("[400] during [GET] to [/home] [methodKey]: [response]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/json;charset=\"UTF-16BE\"",
        "application/json; charset=UTF-16BE",
        "application/json; charset=\"UTF-16BE\"",
        "application/json;charset=UTF-16BE"
      })
  void createFeignExceptionWithCorrectCharsetResponseButDifferentContentTypeFormats(
      String contentType) {
    Map<String, Collection<String>> map = new HashMap<>();
    map.put("connection", new ArrayList<>(Collections.singletonList("keep-alive")));
    map.put("content-length", new ArrayList<>(Collections.singletonList("100")));
    map.put("content-type", new ArrayList<>(Collections.singletonList(contentType)));

    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/home",
            Collections.emptyMap(),
            "data".getBytes(StandardCharsets.UTF_16BE),
            StandardCharsets.UTF_16BE,
            null);

    Response response =
        Response.builder()
            .status(400)
            .body("response".getBytes(StandardCharsets.UTF_16BE))
            .headers(map)
            .request(request)
            .build();

    FeignException exception = FeignException.errorStatus("methodKey", response);
    assertThat(exception.getMessage())
        .isEqualTo("[400] during [GET] to [/home] [methodKey]: [response]");
  }

  @Test
  void createFeignExceptionWithErrorCharsetResponse() {
    Map<String, Collection<String>> map = new HashMap<>();
    map.put("connection", new ArrayList<>(Collections.singletonList("keep-alive")));
    map.put("content-length", new ArrayList<>(Collections.singletonList("100")));
    map.put(
        "content-type",
        new ArrayList<>(Collections.singletonList("application/json;charset=UTF-8")));

    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/home",
            Collections.emptyMap(),
            "data".getBytes(StandardCharsets.UTF_16BE),
            StandardCharsets.UTF_16BE,
            null);

    Response response =
        Response.builder()
            .status(400)
            .body("response".getBytes(StandardCharsets.UTF_16BE))
            .headers(map)
            .request(request)
            .build();

    FeignException exception = FeignException.errorStatus("methodKey", response);
    assertThat(exception.getMessage())
        .isNotEqualTo("[400] during [GET] to [/home] [methodKey]: [response]");
  }

  @Test
  void canGetResponseHeadersFromException() {
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/home",
            Collections.emptyMap(),
            "data".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
            null);

    Map<String, Collection<String>> responseHeaders = new HashMap<>();
    responseHeaders.put("Content-Type", Collections.singletonList("text/plain"));
    responseHeaders.put("Cookie", Arrays.asList("cookie1", "cookie2"));

    Response response =
        Response.builder()
            .request(request)
            .body("some text", StandardCharsets.UTF_8)
            .headers(responseHeaders)
            .build();

    FeignException exception = FeignException.errorStatus("methodKey", response);
    assertThat(exception.responseHeaders())
        .hasEntrySatisfying(
            "Content-Type",
            value -> {
              assertThat(value).contains("text/plain");
            })
        .hasEntrySatisfying(
            "Cookie",
            value -> {
              assertThat(value).contains("cookie1", "cookie2");
            });
  }

  @Test
  void lengthOfBodyExceptionTest() {
    String bigResponse =
        """
        I love a storm in early May
        When springtime’s boisterous, firstborn thunder
        Over the sky will gaily wander
        And growl and roar as though in play.

        A peal, another — gleeful, cheering…
        Rain, raindust… On the trees, behold!-
        The drops hang, each a long pearl earring;
        Bright sunshine paints the thin threads gold.

        A stream downhill goes rushing reckless,
        And in the woods the birds rejoice.
        Din. Clamour. Noise. All nature echoes
        The thunder’s youthful, merry voice.

        You’ll say: ‘Tis laughing, carefree Hebe —
        She fed her father’s eagle, and
        The Storm Cup brimming with a seething
        And bubbling wine dropped from her hand\
        """;

    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/home",
            Collections.emptyMap(),
            "data".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
            null);

    Response response =
        Response.builder()
            .status(400)
            .request(request)
            .body(bigResponse, StandardCharsets.UTF_8)
            .build();

    FeignException defaultException = FeignException.errorStatus("methodKey", response);
    assertThat(defaultException.getMessage().length()).isLessThan(bigResponse.length());

    FeignException customLengthBodyException =
        FeignException.errorStatus("methodKey", response, 4000, 2000);
    assertThat(customLengthBodyException.getMessage().length())
        .isGreaterThanOrEqualTo(bigResponse.length());
  }

  @Test
  void nullRequestShouldThrowNPEwThrowable() {
    assertThrows(
        NullPointerException.class, () -> new Derived(404, "message", null, new Throwable()));
  }

  @Test
  void nullRequestShouldThrowNPEwThrowableAndBytes() {
    assertThrows(
        NullPointerException.class,
        () ->
            new Derived(
                404, "message", null, new Throwable(), new byte[1], Collections.emptyMap()));
  }

  @Test
  void nullRequestShouldThrowNPE() {
    assertThrows(NullPointerException.class, () -> new Derived(404, "message", null));
  }

  @Test
  void nullRequestShouldThrowNPEwBytes() {
    assertThrows(
        NullPointerException.class,
        () -> new Derived(404, "message", null, new byte[1], Collections.emptyMap()));
  }

  static class Derived extends FeignException {

    public Derived(int status, String message, Request request, Throwable cause) {
      super(status, message, request, cause);
    }

    public Derived(
        int status,
        String message,
        Request request,
        Throwable cause,
        byte[] content,
        Map<String, Collection<String>> headers) {
      super(status, message, request, cause, content, headers);
    }

    public Derived(int status, String message, Request request) {
      super(status, message, request);
    }

    public Derived(
        int status,
        String message,
        Request request,
        byte[] content,
        Map<String, Collection<String>> headers) {
      super(status, message, request, content, headers);
    }
  }
}
