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

import feign.*;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import feign.http2client.Http2Client;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.Test;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class Http2ClientTest extends AbstractClientTest {

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
    server.enqueue(new MockResponse.Builder().body("foo").build());

    final TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.patch("some text")).isEqualTo("foo");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("PATCH")
        .hasPath("/patch")
        .hasBody("some text");
  }

  @Override
  @Test
  public void noResponseBodyForPatch() {
    server.enqueue(new MockResponse.Builder().build());

    final TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.patch()).isEmpty();

    MockWebServerAssertions.assertThat(takeRequest()).hasMethod("PATCH").hasPath("/patch");
  }

  private RecordedRequest takeRequest() {
    try {
      return server.takeRequest();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
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
    // MockWebServer rejects GET requests carrying a body ("Request must not have a body"),
    // so this test runs against a minimal local socket server instead.
    try (ServerSocket httpServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      final AtomicReference<String> receivedRequest = new AtomicReference<>();
      final Thread serverThread =
          new Thread(
              () -> {
                try (Socket socket = httpServer.accept()) {
                  socket.setSoTimeout(5000);
                  final InputStream in = socket.getInputStream();
                  final StringBuilder request = new StringBuilder();
                  final byte[] buffer = new byte[8192];
                  while (!request.toString().contains("some request body")) {
                    final int read = in.read(buffer);
                    if (read == -1) {
                      break;
                    }
                    request.append(new String(buffer, 0, read, UTF_8));
                  }
                  receivedRequest.set(request.toString());
                  socket
                      .getOutputStream()
                      .write("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nfoo".getBytes(UTF_8));
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      serverThread.start();

      final TestInterface api =
          newBuilder().target(TestInterface.class, "http://localhost:" + httpServer.getLocalPort());

      assertThat(api.getWithBody()).isEqualTo("foo");

      serverThread.join(5000);
      assertThat(receivedRequest.get())
          .startsWith("GET /anything HTTP/1.1")
          .contains("some request body");
    }
  }

  @Test
  void deleteWithRequestBody() throws Exception {
    server.enqueue(new MockResponse.Builder().body("foo").build());

    final TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.deleteWithBody()).isEqualTo("foo");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("DELETE")
        .hasPath("/anything")
        .hasBody("some request body");
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
