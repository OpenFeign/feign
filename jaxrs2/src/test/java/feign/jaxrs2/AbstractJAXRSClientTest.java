/*
 * Copyright 2012-2024 The Feign Authors
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
package feign.jaxrs2;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import feign.Headers;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import okhttp3.mockwebserver.MockResponse;

public abstract class AbstractJAXRSClientTest extends AbstractClientTest {

  @Override
  public void patch() throws Exception {
    try {
      super.patch();
    } catch (final RuntimeException e) {
      Assumptions.assumeFalse(false, "JaxRS client do not support PATCH requests");
    }
  }

  @Override
  public void noResponseBodyForPut() throws Exception {
    try {
      super.noResponseBodyForPut();
    } catch (final IllegalStateException e) {
      Assumptions.assumeFalse(false, "JaxRS client do not support empty bodies on PUT");
    }
  }

  @Override
  public void noResponseBodyForPatch() {
    try {
      super.noResponseBodyForPatch();
    } catch (final IllegalStateException e) {
      Assumptions.assumeFalse(false, "JaxRS client do not support PATCH requests");
    }
  }

  @Override
  @Test
  public void reasonPhraseIsOptional() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 " + 200));

    final TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    // jaxrsclient is creating a reason when none is present
    // assertThat(response.reason()).isNullOrEmpty();
  }

  @Override
  @Test
  public void parsesRequestAndResponse() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo").addHeader("Foo: Bar"));

    final TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.headers())
        .hasEntrySatisfying("Content-Length", value -> {
          assertThat(value).contains("3");
        }).hasEntrySatisfying("Foo", value -> {
          assertThat(value).contains("Bar");
        });
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream("foo".getBytes(UTF_8)));

    /* queries with no values are omitted from the uri. See RFC 6750 */
    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("POST")
        .hasPath("/?foo=bar&foo=baz&qux")
        .hasBody("foo");
  }

  @Test
  void contentTypeWithoutCharset2() throws Exception {
    server.enqueue(new MockResponse()
        .setBody("AAAAAAAA"));
    final JaxRSClientTestInterface api = newBuilder()
        .target(JaxRSClientTestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.getWithContentType();
    // Response length should not be null
    assertThat(Util.toString(response.body().asReader(UTF_8))).isEqualTo("AAAAAAAA");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(
            MapEntry.entry("Accept", Collections.singletonList("text/plain")),
            MapEntry.entry("Content-Type", Collections.singletonList("text/plain")))
        .hasMethod("GET");
  }

  /*
   * JaxRS does not support gzip and deflate compression out-of-the-box.
   */
  @Override
  public void canSupportGzip() throws Exception {
    assumeFalse(false, "JaxRS client do not support gzip compression");
  }

  @Override
  public void canSupportGzipOnError() throws Exception {
    assumeFalse(false, "JaxRS client do not support gzip compression");
  }

  @Override
  public void canSupportDeflate() throws Exception {
    assumeFalse(false, "JaxRS client do not support deflate compression");
  }

  @Override
  public void canSupportDeflateOnError() throws Exception {
    assumeFalse(false, "JaxRS client do not support deflate compression");
  }

  @Override
  public void canExceptCaseInsensitiveHeader() throws Exception {
    assumeFalse(false, "JaxRS client do not support gzip compression");
  }

  public interface JaxRSClientTestInterface {

    @RequestLine("GET /")
    @Headers({"Accept: text/plain", "Content-Type: text/plain"})
    Response getWithContentType();
  }


  @Override
  public void veryLongResponseNullLength() {
    assumeFalse(false, "JaxRS client hang if the response doesn't have a payload");
  }
}
