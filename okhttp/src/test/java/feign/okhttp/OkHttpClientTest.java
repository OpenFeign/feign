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
package feign.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;
import feign.Feign;
import feign.Feign.Builder;
import feign.Headers;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import mockwebserver3.MockResponse;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class OkHttpClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new OkHttpClient());
  }


  @Test
  public void testContentTypeWithoutCharset() throws Exception {
    server.enqueue(new MockResponse()
        .setBody("AAAAAAAA"));
    OkHttpClientTestInterface api = newBuilder()
        .target(OkHttpClientTestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getWithContentType();
    // Response length should not be null
    assertThat(Util.toString(response.body().asReader(Util.UTF_8))).isEqualTo("AAAAAAAA");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(
            MapEntry.entry("Accept", Collections.singletonList("text/plain")),
            MapEntry.entry("Content-Type", Collections.singletonList("text/plain")))
        .hasMethod("GET");
  }


  @Test
  void noFollowRedirect() throws Exception {
    server.enqueue(
        new MockResponse().setResponseCode(302).addHeader("Location", server.url("redirect")));
    // Enqueue a response to fail fast if the redirect is followed, instead of waiting for the
    // timeout
    server.enqueue(new MockResponse().setBody("Hello"));

    OkHttpClientTestInterface api = newBuilder()
        // Use the same connect and read timeouts as the OkHttp default
        .options(new Request.Options(10_000, TimeUnit.MILLISECONDS, 10_000, TimeUnit.MILLISECONDS,
            false))
        .target(OkHttpClientTestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.get();
    // Response length should not be null
    assertThat(response.status()).isEqualTo(302);
    assertThat(response.headers().get("Location").iterator().next())
        .isEqualTo(server.url("redirect").toString());

  }


  @Test
  void followRedirect() throws Exception {
    String expectedBody = "Hello";

    server.enqueue(
        new MockResponse().setResponseCode(302).addHeader("Location", server.url("redirect")));
    server.enqueue(new MockResponse().setBody(expectedBody));

    OkHttpClientTestInterface api = newBuilder()
        // Use the same connect and read timeouts as the OkHttp default
        .options(new Request.Options(10_000, TimeUnit.MILLISECONDS, 10_000, TimeUnit.MILLISECONDS,
            true))
        .target(OkHttpClientTestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.get();
    // Response length should not be null
    assertThat(response.status()).isEqualTo(200);
    String payload = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
    assertThat(payload).isEqualTo(expectedBody);

  }

  /*
   * OkHTTP does not support gzip and deflate compression out-of-the-box. But you can add an
   * interceptor that implies it, see
   * https://stackoverflow.com/questions/51901333/okhttp-3-how-to-decompress-gzip-deflate-response-
   * manually-using-java-android
   */
  @Override
  public void canSupportGzip() throws Exception {
    assumeFalse(false, "OkHTTP client do not support gzip compression");
  }

  @Override
  public void canSupportGzipOnError() throws Exception {
    assumeFalse(false, "OkHTTP client do not support gzip compression");
  }

  @Override
  public void canSupportDeflate() throws Exception {
    assumeFalse(false, "OkHTTP client do not support deflate compression");
  }

  @Override
  public void canSupportDeflateOnError() throws Exception {
    assumeFalse(false, "OkHTTP client do not support deflate compression");
  }

  @Override
  public void canExceptCaseInsensitiveHeader() throws Exception {
    assumeFalse(false, "OkHTTP client do not support gzip compression");
  }

  public interface OkHttpClientTestInterface {

    @RequestLine("GET /")
    @Headers({"Accept: text/plain", "Content-Type: text/plain"})
    Response getWithContentType();

    @RequestLine("GET /")
    Response get();
  }
}
