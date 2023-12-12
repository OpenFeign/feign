/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.codec;

import static feign.Util.RETRY_AFTER;
import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;

@SuppressWarnings("deprecation")
class DefaultErrorDecoderTest {

  private ErrorDecoder errorDecoder = new ErrorDecoder.Default();

  private Map<String, Collection<String>> headers = new LinkedHashMap<>();

  @Test
  void throwsFeignException() throws Throwable {

    Response response = Response.builder().status(500).reason("Internal server error")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers).build();

    Throwable exception = errorDecoder.decode("Service#foo()", response);
    assertThat(exception.getMessage())
        .contains("[500 Internal server error] during [GET] to [/api] [Service#foo()]: []");
  }

  @Test
  void throwsFeignExceptionIncludingBody() throws Throwable {
    Response response = Response.builder().status(500).reason("Internal server error")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers).body("hello world", UTF_8).build();

    try {
      throw errorDecoder.decode("Service#foo()", response);
    } catch (FeignException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "[500 Internal server error] during [GET] to [/api] [Service#foo()]: [hello world]");
      assertThat(e.contentUTF8()).isEqualTo("hello world");
    }
  }

  @Test
  void throwsFeignExceptionIncludingLongBody() throws Throwable {
    String actualBody = repeatString("hello world ", 200);
    Response response = Response.builder().status(500).reason("Internal server error")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers).body(actualBody, UTF_8).build();
    String expectedBody = repeatString("hello world ", 16) + "hello wo... (2400 bytes)";

    try {
      throw errorDecoder.decode("Service#foo()", response);
    } catch (FeignException e) {
      assertThat(e.getMessage()).isEqualTo(
          "[500 Internal server error] during [GET] to [/api] [Service#foo()]: [" + expectedBody
              + "]");
      assertThat(e.contentUTF8()).isEqualTo(actualBody);
    }
  }

  private String repeatString(String string, int times) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < times; i++) {
      result.append(string);
    }
    return result.toString();
  }

  @Test
  void feignExceptionIncludesStatus() {
    Response response = Response.builder().status(400).reason("Bad request")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers).build();

    Exception exception = errorDecoder.decode("Service#foo()", response);

    assertThat(exception).isInstanceOf(FeignException.class);
    assertThat(((FeignException) exception).status()).isEqualTo(400);
  }

  @Test
  void retryAfterHeaderThrowsRetryableException() throws Throwable {

    headers.put(RETRY_AFTER, Collections.singletonList("Sat, 1 Jan 2000 00:00:00 GMT"));
    Response response = Response.builder().status(503).reason("Service Unavailable")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers).build();

    Throwable exception = errorDecoder.decode("Service#foo()", response);
    assertThat(exception.getMessage())
        .contains("[503 Service Unavailable] during [GET] to [/api] [Service#foo()]: []");
  }

  @Test
  void lengthOfBodyExceptionTest() {
    Response response = bigBodyResponse();
    Exception defaultException = errorDecoder.decode("Service#foo()", response);
    assertThat(defaultException.getMessage().length()).isLessThan(response.body().length());

    ErrorDecoder customizedErrorDecoder = new ErrorDecoder.Default(4000, 2000);
    Exception customizedException = customizedErrorDecoder.decode("Service#foo()", response);
    assertThat(customizedException.getMessage().length())
        .isGreaterThanOrEqualTo(response.body().length());
  }

  private Response bigBodyResponse() {
    String content = "I love a storm in early May\n"
        + "When springtime’s boisterous, firstborn thunder\n"
        + "Over the sky will gaily wander\n" + "And growl and roar as though in play.\n" + "\n"
        + "A peal, another — gleeful, cheering…\n" + "Rain, raindust… On the trees, behold!-\n"
        + "The drops hang, each a long pearl earring;\n"
        + "Bright sunshine paints the thin threads gold.\n"
        + "\n" + "A stream downhill goes rushing reckless,\n"
        + "And in the woods the birds rejoice.\n"
        + "Din. Clamour. Noise. All nature echoes\n" + "The thunder’s youthful, merry voice.\n"
        + "\n"
        + "You’ll say: ‘Tis laughing, carefree Hebe —\n" + "She fed her father’s eagle, and\n"
        + "The Storm Cup brimming with a seething\n" + "And bubbling wine dropped from her hand";

    InputStream inputStream = new ByteArrayInputStream(content.getBytes(UTF_8));
    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singleton("text/plain"));
    return Response.builder().status(400).request(Request.create(Request.HttpMethod.GET, "/home",
        Collections.emptyMap(), "data".getBytes(Util.UTF_8), Util.UTF_8, null))
        .body(content, Util.UTF_8)
        .build();
  }
}
