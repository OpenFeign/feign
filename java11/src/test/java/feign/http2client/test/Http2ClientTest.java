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
package feign.http2client.test;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import feign.*;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import feign.http2client.Http2Client;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class Http2ClientTest extends AbstractClientTest {

  private static final boolean HTTPBIN_REACHABLE;

  static {
    boolean reachable = false;
    try {
      java.net.InetAddress.getByName("nghttp2.org");
      reachable = true;
    } catch (java.net.UnknownHostException _) {
      // ignore
    }
    HTTPBIN_REACHABLE = reachable;
  }

  public interface TestInterface {
    @RequestLine("PATCH /patch")
    @Headers({"Accept: text/plain"})
    String patch(String var1);

    @RequestLine("PATCH /patch")
    @Headers({"Accept: text/plain"})
    String patch();

    @RequestLine("POST /timeout")
    @Headers({"Accept: text/plain"})
    String timeout();

    @RequestLine("GET /anything")
    @Body("some request body")
    String getWithBody();

    @RequestLine("DELETE /anything")
    @Body("some request body")
    String deleteWithBody();

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: text/plain"})
    Response post(String body);

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
  }

  @Override
  @Test
  public void patch() throws Exception {
    assumeTrue(HTTPBIN_REACHABLE, "nghttp2.org is not reachable");
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    assertThat(api.patch("")).contains("https://nghttp2.org/httpbin/patch");
  }

  @Override
  @Test
  public void noResponseBodyForPatch() {
    assumeTrue(HTTPBIN_REACHABLE, "nghttp2.org is not reachable");
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    assertThat(api.patch()).contains("https://nghttp2.org/httpbin/patch");
  }

  @Override
  @Test
  public void reasonPhraseIsOptional() throws IOException, InterruptedException {
    server.enqueue(new MockResponse.Builder().status("HTTP/1.1 " + 200).build());

    final AbstractClientTest.TestInterface api =
        newBuilder()
            .target(AbstractClientTest.TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isNull();
  }

  @Test
  void reasonPhraseInHeader() throws Exception {
    server.enqueue(
        new MockResponse.Builder()
            .addHeader("Reason-Phrase", "There is A reason")
            .status("HTTP/1.1 " + 200)
            .build());

    final AbstractClientTest.TestInterface api =
        newBuilder()
            .target(AbstractClientTest.TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("There is A reason");
  }

  @Override
  @Test
  public void veryLongResponseNullLength() {
    // client is too smart to fall for a body that is 8 bytes long
  }

  @Test
  void timeoutTest() {
    server.enqueue(
        new MockResponse.Builder().body("foo").headersDelay(1, TimeUnit.SECONDS).build());

    final TestInterface api =
        newBuilder()
            .retryer(Retryer.NEVER_RETRY)
            .options(
                new Request.Options(500, TimeUnit.MILLISECONDS, 500, TimeUnit.MILLISECONDS, true))
            .target(TestInterface.class, server.url("/").toString());

    FeignException exception =
        assertThatExceptionOfType(FeignException.class).isThrownBy(() -> api.timeout()).actual();
    assertThat(exception).hasCauseInstanceOf(HttpTimeoutException.class);
  }

  @Test
  void getWithRequestBody() throws Exception {
    assumeTrue(HTTPBIN_REACHABLE, "nghttp2.org is not reachable");
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    String result = api.getWithBody();
    String expected = "{ \"data\":\"some request body\" }";
    JSONAssert.assertEquals(expected, result, false);
  }

  @Test
  void deleteWithRequestBody() throws Exception {
    assumeTrue(HTTPBIN_REACHABLE, "nghttp2.org is not reachable");
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    String result = api.deleteWithBody();
    String expected = "{ \"data\":\"some request body\" }";
    JSONAssert.assertEquals(expected, result, false);
  }

  @Override
  @Test
  public void parsesResponseMissingLength() throws IOException {
    server.enqueue(new MockResponse.Builder().chunkedBody("foo", 1).build());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("testing");
    assertThat(response.status()).isEqualTo(200);
    // assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.body().length()).isNull();
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream("foo".getBytes(UTF_8)));
  }

  @Override
  @Test
  public void parsesErrorResponse() {

    server.enqueue(new MockResponse.Builder().code(500).body("ARGHH").build());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Throwable exception =
        assertThatExceptionOfType(FeignException.class).isThrownBy(() -> api.get()).actual();
    assertThat(exception.getMessage())
        .contains(
            "[500] during [GET] to [http://localhost:"
                + server.getPort()
                + "/] [TestInterface#get()]: [ARGHH]");
  }

  @Override
  @Test
  public void defaultCollectionFormat() throws Exception {
    server.enqueue(new MockResponse.Builder().body("body").build());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.get(Arrays.asList("bar", "baz"));

    assertThat(response.status()).isEqualTo(200);
    // assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("GET")
        .hasPath("/?foo=bar&foo=baz");
  }

  @Override
  @Test
  public void headersWithNotEmptyParams() throws InterruptedException {
    server.enqueue(new MockResponse.Builder().body("body").build());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getWithHeaders("token");

    assertThat(response.status()).isEqualTo(200);
    // assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("GET")
        .hasPath("/")
        .hasHeaders(entry("authorization", Collections.singletonList("token")));
  }

  @Override
  @Test
  public void headersWithNullParams() throws InterruptedException {
    server.enqueue(new MockResponse.Builder().body("body").build());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getWithHeaders(null);

    assertThat(response.status()).isEqualTo(200);
    // assertThat(response.reason()).isEqualTo("OK");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("GET")
        .hasPath("/")
        .hasNoHeaderNamed("Authorization");
  }

  @Test
  public void alternativeCollectionFormat() throws Exception {
    server.enqueue(new MockResponse.Builder().body("body").build());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getCSV(Arrays.asList("bar", "baz"));

    assertThat(response.status()).isEqualTo(200);
    // assertThat(response.reason()).isEqualTo("OK");

    // Some HTTP libraries percent-encode commas in query parameters and others
    // don't.
    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("GET")
        .hasOneOfPath("/?foo=bar,baz", "/?foo=bar%2Cbaz");
  }

  @Override
  @Test
  public void parsesRequestAndResponse() throws IOException, InterruptedException {
    server.enqueue(new MockResponse.Builder().body("foo").addHeader("Foo: Bar").build());

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    // assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.headers())
        .hasEntrySatisfying(
            "Content-Length",
            value -> {
              assertThat(value).contains("3");
            })
        .hasEntrySatisfying(
            "Foo",
            value -> {
              assertThat(value).contains("Bar");
            });
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream("foo".getBytes(UTF_8)));

    RecordedRequest recordedRequest = server.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualToIgnoringCase("POST");
    assertThat(recordedRequest.getHeaders().get("Foo")).isEqualToIgnoringCase("Bar, Baz");
    assertThat(recordedRequest.getHeaders().get("Accept")).isEqualToIgnoringCase("*/*");
    assertThat(recordedRequest.getHeaders().get("Content-Length")).isEqualToIgnoringCase("3");
    assertThat(recordedRequest.getBody().utf8()).isEqualToIgnoringCase("foo");
  }

  @Override
  public Feign.Builder newBuilder() {
    return Feign.builder().client(new Http2Client());
  }
}
