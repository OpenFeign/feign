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
package feign.client;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import feign.Client;
import feign.CollectionFormat;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Headers;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okio.Buffer;

/**
 * {@link AbstractClientTest} can be extended to run a set of tests against any {@link Client}
 * implementation.
 */
public abstract class AbstractClientTest {
  public final MockWebServer server = new MockWebServer();

  /**
   * Create a Feign {@link Builder} with a client configured
   */
  public abstract Builder newBuilder();

  /**
   * Some client implementation tests should override this test if the PATCH operation is
   * unsupported.
   */
  @Test
  public void patch() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));
    server.enqueue(new MockResponse());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.patch("")).isEqualTo("foo");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(entry("Accept", Collections.singletonList("text/plain")),
            entry("Content-Length", Collections.singletonList("0")))
        .hasNoHeaderNamed("Content-Type").hasMethod("PATCH");
  }

  @Test
  public void parsesRequestAndResponse() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo").addHeader("Foo: Bar"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.headers())
        .hasEntrySatisfying("Content-Length", value -> {
          assertThat(value).contains("3");
        })
        .hasEntrySatisfying("Foo", value -> {
          assertThat(value).contains("Bar");
        });
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream("foo".getBytes(UTF_8)));

    RecordedRequest recordedRequest = server.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualToIgnoringCase("POST");
    assertThat(recordedRequest.getHeader("Foo")).isEqualToIgnoringCase("Bar, Baz");
    assertThat(recordedRequest.getHeader("Accept")).isEqualToIgnoringCase("*/*");
    assertThat(recordedRequest.getHeader("Content-Length")).isEqualToIgnoringCase("3");
    assertThat(recordedRequest.getBody().readUtf8()).isEqualToIgnoringCase("foo");
  }

  @Test
  public void reasonPhraseIsOptional() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 " + 200));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isNullOrEmpty();
  }

  @Test
  void parsesErrorResponse() {

    server.enqueue(new MockResponse().setResponseCode(500).setBody("ARGHH"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Throwable exception = assertThrows(FeignException.class, () -> api.get());
    assertThat(exception.getMessage())
        .contains("[500 Server Error] during [GET] to [http://localhost:"
            + server.getPort() + "/] [TestInterface#get()]: [ARGHH]");
  }

  @Test
  void parsesErrorResponseBody() {
    String expectedResponseBody = "ARGHH";

    server.enqueue(new MockResponse().setResponseCode(500).setBody("ARGHH"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    try {
      api.get();
    } catch (FeignException e) {
      assertThat(e.contentUTF8()).isEqualTo(expectedResponseBody);
    }
  }

  @Test
  public void parsesUnauthorizedResponseBody() {
    String expectedResponseBody = "ARGHH";

    server.enqueue(new MockResponse().setResponseCode(401).setBody("ARGHH"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    try {
      api.postForString("HELLO");
    } catch (FeignException e) {
      assertThat(e.contentUTF8()).isEqualTo(expectedResponseBody);
    }
  }

  @Test
  void safeRebuffering() {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = newBuilder().logger(new Logger() {
      @Override
      protected void log(String configKey, String format, Object... args) {}
    }).logLevel(Logger.Level.FULL) // rebuffers the body
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.post("foo");
  }

  /**
   * This shows that is a no-op or otherwise doesn't cause an NPE when there's no content.
   */
  @Test
  void safeRebuffering_noContent() {
    server.enqueue(new MockResponse().setResponseCode(204));

    TestInterface api = newBuilder().logger(new Logger() {
      @Override
      protected void log(String configKey, String format, Object... args) {}
    }).logLevel(Logger.Level.FULL) // rebuffers the body
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.post("foo");
  }

  @Test
  void noResponseBodyForPost() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    api.noPostBody();
  }

  @Test
  public void noResponseBodyForPut() throws Exception {
    server.enqueue(new MockResponse());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    api.noPutBody();
  }

  /**
   * Some client implementation tests should override this test if the PATCH operation is
   * unsupported.
   */
  @Test
  public void noResponseBodyForPatch() {
    server.enqueue(new MockResponse());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    api.noPatchBody();
  }

  @Test
  void parsesResponseMissingLength() throws IOException {
    server.enqueue(new MockResponse().setChunkedBody("foo", 1));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("testing");
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.body().length()).isNull();
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream("foo".getBytes(UTF_8)));
  }

  @Test
  void postWithSpacesInPath() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());
    api.post("current documents", "foo");

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("POST")
        .hasPath("/path/current%20documents/resource").hasBody("foo");
  }

  @Test
  public void veryLongResponseNullLength() {
    server.enqueue(
        new MockResponse().setBody("AAAAAAAA").addHeader("Content-Length", Long.MAX_VALUE));
    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");
    // Response length greater than Integer.MAX_VALUE should be null
    assertThat(response.body().length()).isNull();
  }

  @Test
  void responseLength() {
    server.enqueue(new MockResponse().setBody("test"));
    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Integer expected = 4;
    Response response = api.post("");
    Integer actual = response.body().length();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void contentTypeWithCharset() throws Exception {
    server.enqueue(new MockResponse().setBody("AAAAAAAA"));
    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.postWithContentType("foo", "text/plain;charset=utf-8");
    // Response length should not be null
    assertThat(Util.toString(response.body().asReader(UTF_8))).isEqualTo("AAAAAAAA");
  }

  @Test
  void contentTypeWithoutCharset() throws Exception {
    server.enqueue(new MockResponse().setBody("AAAAAAAA"));
    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.postWithContentType("foo", "text/plain");
    // Response length should not be null
    assertThat(Util.toString(response.body().asReader(UTF_8))).isEqualTo("AAAAAAAA");
  }

  @Test
  public void contentTypeDefaultsToRequestCharset() throws Exception {
    server.enqueue(new MockResponse().setBody("foo"));
    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    // should use utf-8 encoding by default
    api.postWithContentType("àáâãäåèéêë", "text/plain; charset=UTF-8");

    String body = server.takeRequest().getBody().readUtf8();
    assertThat(body).isEqualToIgnoringCase("àáâãäåèéêë");
  }

  @Test
  void defaultCollectionFormat() throws Exception {
    server.enqueue(new MockResponse().setBody("body"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.get(Arrays.asList("bar", "baz"));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET")
        .hasPath("/?foo=bar&foo=baz");
  }

  @Test
  void headersWithNullParams() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("body"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getWithHeaders(null);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET").hasPath("/")
        .hasNoHeaderNamed("Authorization");
  }

  @Test
  void headersWithNotEmptyParams() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("body"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getWithHeaders("token");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET").hasPath("/")
        .hasHeaders(entry("authorization", Collections.singletonList("token")));
  }

  @Test
  void alternativeCollectionFormat() throws Exception {
    server.enqueue(new MockResponse().setBody("body"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getCSV(Arrays.asList("bar", "baz"));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");

    // Some HTTP libraries percent-encode commas in query parameters and others
    // don't.
    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET").hasOneOfPath(
        "/?foo=bar,baz",
        "/?foo=bar%2Cbaz");
  }

  @Test
  public void canSupportGzip() throws Exception {
    /* enqueue a zipped response */
    final String responseData = "Compressed Data";
    server.enqueue(new MockResponse().addHeader("Content-Encoding", "gzip")
        .setBody(new Buffer().write(compress(responseData))));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    String result = api.get();

    /* verify that the response is unzipped */
    assertThat(result).isNotNull().isEqualToIgnoringCase(responseData);
  }

  @Test
  public void canSupportGzipOnError() throws Exception {
    /* enqueue a zipped response */
    final String responseData = "Compressed Data";
    server.enqueue(new MockResponse().setResponseCode(400).addHeader("Content-Encoding", "gzip")
        .setBody(new Buffer().write(compress(responseData))));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    try {
      api.get();
      fail("Expect FeignException");
    } catch (FeignException e) {
      /* verify that the response is unzipped */
      assertThat(e.responseBody()).isNotEmpty()
          .map(body -> new String(body.array(), StandardCharsets.UTF_8))
          .get().isEqualTo(responseData);
    }

  }

  @Test
  public void canSupportDeflate() throws Exception {
    /* enqueue a zipped response */
    final String responseData = "Compressed Data";
    server.enqueue(new MockResponse().addHeader("Content-Encoding", "deflate")
        .setBody(new Buffer().write(deflate(responseData))));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    String result = api.get();

    /* verify that the response is unzipped */
    assertThat(result).isNotNull().isEqualToIgnoringCase(responseData);
  }

  @Test
  public void canSupportDeflateOnError() throws Exception {
    /* enqueue a zipped response */
    final String responseData = "Compressed Data";
    server.enqueue(new MockResponse().setResponseCode(400).addHeader("Content-Encoding", "deflate")
        .setBody(new Buffer().write(deflate(responseData))));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    try {
      api.get();
      fail("Expect FeignException");
    } catch (FeignException e) {
      /* verify that the response is unzipped */
      assertThat(e.responseBody()).isNotEmpty()
          .map(body -> new String(body.array(), StandardCharsets.UTF_8))
          .get().isEqualTo(responseData);
    }
  }

  @Test
  public void canExceptCaseInsensitiveHeader() throws Exception {
    /* enqueue a zipped response */
    final String responseData = "Compressed Data";
    server.enqueue(new MockResponse().addHeader("content-encoding", "gzip")
        .setBody(new Buffer().write(compress(responseData))));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    String result = api.get();

    /* verify that the response is unzipped */
    assertThat(result).isNotNull().isEqualToIgnoringCase(responseData);
  }

  @SuppressWarnings("UnusedReturnValue")
  public interface TestInterface {

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: text/plain"})
    Response post(String body);

    @RequestLine("POST /path/{to}/resource")
    @Headers("Accept: text/plain")
    Response post(@Param("to") String to, String body);

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: text/plain"})
    String postForString(String body);

    @RequestLine("GET /")
    @Headers("Accept: text/plain")
    String get();

    @RequestLine("GET /?foo={multiFoo}")
    Response get(@Param("multiFoo") List<String> multiFoo);

    @Headers({"Authorization: {authorization}"})
    @RequestLine("GET /")
    Response getWithHeaders(@Param("authorization") String authorization);

    @RequestLine(value = "GET /?foo={multiFoo}", collectionFormat = CollectionFormat.CSV)
    Response getCSV(@Param("multiFoo") List<String> multiFoo);

    @RequestLine("PATCH /")
    @Headers("Accept: text/plain")
    String patch(String body);

    @RequestLine("POST")
    String noPostBody();

    @RequestLine("PUT")
    String noPutBody();

    @RequestLine("PATCH")
    String noPatchBody();

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: {contentType}"})
    Response postWithContentType(String body, @Param("contentType") String contentType);
  }

  private byte[] compress(String data) throws Exception {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length())) {
      GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bos);
      gzipOutputStream.write(data.getBytes(StandardCharsets.UTF_8), 0, data.length());
      gzipOutputStream.close();
      return bos.toByteArray();
    }
  }

  private byte[] deflate(String data) throws Exception {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length())) {
      DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(bos);
      deflaterOutputStream.write(data.getBytes(StandardCharsets.UTF_8), 0, data.length());
      deflaterOutputStream.close();
      return bos.toByteArray();
    }
  }

  @AfterEach
  void afterEachTest() throws IOException {
    server.close();
  }

}
