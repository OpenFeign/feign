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
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;

import feign.*;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import feign.http2client.Http2Client;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
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
    server.enqueue(new MockResponse().setBody("foo"));

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
    server.enqueue(new MockResponse());

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
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 " + 200));

    final AbstractClientTest.TestInterface api =
        newBuilder()
            .target(AbstractClientTest.TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isNull();
  }

  @Test
  void reasonPhraseInHeader() throws IOException, InterruptedException {
    server.enqueue(
        new MockResponse()
            .addHeader("Reason-Phrase", "There is A reason")
            .setStatus("HTTP/1.1 " + 200));

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
    server.enqueue(new MockResponse().setBody("foo").setHeadersDelay(1, TimeUnit.SECONDS));

    final TestInterface api =
        newBuilder()
            .retryer(Retryer.NEVER_RETRY)
            .options(
                new Request.Options(500, TimeUnit.MILLISECONDS, 500, TimeUnit.MILLISECONDS, true))
            .target(TestInterface.class, server.url("/").toString());

    FeignException exception = assertThrows(FeignException.class, () -> api.timeout());
    assertThat(exception).hasCauseInstanceOf(HttpTimeoutException.class);
  }

  @Test
  void timeoutReadingResponseBody() {
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final TestInterface api =
        newBuilder()
            .retryer(Retryer.NEVER_RETRY)
            .options(
                new Request.Options(500, TimeUnit.MILLISECONDS, 500, TimeUnit.MILLISECONDS, true))
            .target(TestInterface.class, server.url("/").toString());

    FeignException exception = assertThrows(FeignException.class, () -> api.timeout());
    assertThat(exception).hasCauseInstanceOf(HttpTimeoutException.class);
  }

  @Test
  void readTimeoutIsIdleTimeoutNotWholeBodyDeadline() {
    server.enqueue(
        new MockResponse().setBody("0123456789").throttleBody(1, 100, TimeUnit.MILLISECONDS));

    final TestInterface api =
        newBuilder()
            .retryer(Retryer.NEVER_RETRY)
            .options(
                new Request.Options(500, TimeUnit.MILLISECONDS, 500, TimeUnit.MILLISECONDS, true))
            .target(TestInterface.class, server.url("/").toString());

    assertThat(api.get()).isEqualTo("0123456789");
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
    server.enqueue(new MockResponse().setBody("foo"));

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
    server.enqueue(new MockResponse().setChunkedBody("foo", 1));

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

    server.enqueue(new MockResponse().setResponseCode(500).setBody("ARGHH"));

    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Throwable exception = assertThrows(FeignException.class, () -> api.get());
    assertThat(exception.getMessage())
        .contains(
            "[500] during [GET] to [http://localhost:"
                + server.getPort()
                + "/] [TestInterface#get()]: [ARGHH]");
  }

  @Override
  @Test
  public void defaultCollectionFormat() throws Exception {
    server.enqueue(new MockResponse().setBody("body"));

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
    server.enqueue(new MockResponse().setBody("body"));

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
    server.enqueue(new MockResponse().setBody("body"));

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
    server.enqueue(new MockResponse().setBody("body"));

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
    server.enqueue(new MockResponse().setBody("foo").addHeader("Foo: Bar"));

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
    assertThat(recordedRequest.getHeader("Foo")).isEqualToIgnoringCase("Bar, Baz");
    assertThat(recordedRequest.getHeader("Accept")).isEqualToIgnoringCase("*/*");
    assertThat(recordedRequest.getHeader("Content-Length")).isEqualToIgnoringCase("3");
    assertThat(recordedRequest.getBody().readUtf8()).isEqualToIgnoringCase("foo");
  }

  /**
   * By default the response head is not buffered: {@code execute()} returns as soon as the headers
   * arrive and the body is streamed lazily, as it has since the switch to {@code
   * BodyHandlers.ofInputStream()}. Streaming consumers - SSE, long polling, chunked streams - rely
   * on getting the {@link Response} back before the body is complete.
   *
   * <p>The server sends a first chunk behind the headers and holds the rest back until this test
   * has the {@link Response} in hand. An {@code execute()} that read the body eagerly could only
   * return after the read timeout, with an exception.
   */
  @Test
  void responseIsStreamedLazilyByDefault() throws Exception {
    final CountDownLatch restOfBody = new CountDownLatch(1);
    try (ServerSocket httpServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      final Thread serverThread =
          new Thread(
              () -> {
                try (Socket socket = httpServer.accept()) {
                  socket.setSoTimeout(10000);
                  drainRequest(socket.getInputStream());
                  final OutputStream out = socket.getOutputStream();
                  out.write(
                      ("HTTP/1.1 200 OK\r\n"
                              + "Transfer-Encoding: chunked\r\n"
                              + "\r\n"
                              + "5\r\nfirst\r\n")
                          .getBytes(UTF_8));
                  out.flush();
                  restOfBody.await(10, TimeUnit.SECONDS);
                  out.write(("6\r\nsecond\r\n" + "0\r\n\r\n").getBytes(UTF_8));
                  out.flush();
                } catch (IOException | InterruptedException e) {
                  // the client is gone, there is nothing left to stream
                }
              });
      serverThread.start();

      final TestInterface api =
          newBuilder()
              .retryer(Retryer.NEVER_RETRY)
              .options(
                  new Request.Options(
                      10000, TimeUnit.MILLISECONDS, 2000, TimeUnit.MILLISECONDS, true))
              .target(TestInterface.class, "http://localhost:" + httpServer.getLocalPort());

      try {
        // returns with the headers in, while the server still holds the second chunk
        final Response response = api.post("some text");
        assertThat(restOfBody.getCount()).isEqualTo(1);

        restOfBody.countDown();
        assertThat(response.body().asInputStream())
            .hasSameContentAs(new ByteArrayInputStream("firstsecond".getBytes(UTF_8)));
      } finally {
        restOfBody.countDown();
      }

      httpServer.close();
      serverThread.join(5000);
    }
  }

  /**
   * Without opting in, a transport failure on the body keeps its usual classification: {@code
   * execute()} returns normally once the headers arrive, and the failure surfaces when the body is
   * read - outside of feign for a {@link Response} return type, and as the non-retryable {@link
   * FeignException} of {@code errorReading} for a decoded return type.
   */
  @Test
  void malformedResponseChunkIsReadLazilyByDefault() throws Exception {
    try (ServerSocket httpServer = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
      final Thread serverThread = startMalformedChunkServer(httpServer);
      final AtomicInteger attempts = new AtomicInteger();
      final TestInterface api =
          newBuilder()
              .retryer(new CountingRetryer(attempts))
              .target(TestInterface.class, "http://localhost:" + httpServer.getLocalPort());

      // execute() returned normally: with a Response return type the caller hits the malformed
      // chunk when reading the body, and it escapes as the raw IOException
      final Response response = api.post("some text");
      assertThat(response.status()).isEqualTo(200);
      assertThrows(IOException.class, () -> response.body().asInputStream().readAllBytes());

      // a decoded return type fails inside feign, as the non-retryable FeignException that
      // errorReading produces once execute() has returned
      final FeignException exception = assertThrows(FeignException.class, () -> api.get());
      assertThat(exception).isNotInstanceOf(RetryableException.class);

      // nothing was classified as retryable, so the retryer was never consulted
      assertThat(attempts.get()).isZero();

      httpServer.close();
      serverThread.join(5000);
    }
  }

  /**
   * Regression test for issue #2689, for a client created with {@code bufferResponseHead} enabled:
   * with the body streamed lazily, a malformed response chunk fails only when the body is read,
   * after {@code Http2Client#execute} returned. The IOException then escaped the feign invocation
   * instead of being classified as a retryable {@link RetryableException}, so retries never fired.
   * Reading the head during {@code execute()} brings the failure back into the invocation.
   */
  @Test
  void bufferedResponseHeadSurfacesMalformedChunkAsRetryableException() throws Exception {
    // three attempts per invocation, two invocations: a retryer that makes an attempt only
    // after the previous failure was classified as retryable
    final int expectedAttempts = 6;
    try (ServerSocket httpServer = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
      final Thread serverThread = startMalformedChunkServer(httpServer);
      final AtomicInteger attempts = new AtomicInteger();
      final TestInterface api =
          newBuilderWithBufferedResponseHead()
              .retryer(new CountingRetryer(attempts))
              .target(TestInterface.class, "http://localhost:" + httpServer.getLocalPort());

      // a Response return type reads the body outside of feign: the malformed chunk must not
      // escape as a raw IOException, it must surface as a retryable exception
      assertThrows(RetryableException.class, () -> api.post("some text"));

      // a decoded return type must fail as retryable too, not as the non-retryable
      // FeignException that errorReading produces once execute() has returned
      assertThrows(RetryableException.class, () -> api.get());

      // every attempt failed as retryable and was retried until the retryer gave up
      assertThat(attempts.get()).isEqualTo(expectedAttempts);

      httpServer.close();
      serverThread.join(5000);
    }
  }

  /**
   * With the head buffered, a body that does not fit the buffer keeps streaming: the buffered head
   * is re-attached in front of the remaining stream.
   */
  @Test
  void bufferedResponseHeadKeepsStreamingBodiesLargerThanTheBuffer() throws IOException {
    // well past the 8KB read during execute()
    final char[] chars = new char[100 * 1024];
    Arrays.fill(chars, 'a');
    final String body = new String(chars);
    server.enqueue(new MockResponse().setChunkedBody(body, 1024));
    server.enqueue(new MockResponse().setChunkedBody(body, 1024));

    // a decoded return type
    assertThat(
            newBuilderWithBufferedResponseHead()
                .target(TestInterface.class, server.url("/").toString())
                .get())
        .isEqualTo(body);

    // and a Response return type, whose body is read by the caller
    final Response response =
        newBuilderWithBufferedResponseHead()
            .target(TestInterface.class, server.url("/").toString())
            .post("foo");
    assertThat(response.body().length()).isNull();
    assertThat(response.body().asInputStream())
        .hasSameContentAs(new ByteArrayInputStream(body.getBytes(UTF_8)));
  }

  /**
   * A builder around a client that reads the head of the response body during {@code execute()}.
   */
  private static Feign.Builder newBuilderWithBufferedResponseHead() {
    return Feign.builder()
        .client(
            new Http2Client(
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofMillis(10000))
                    .build(),
                true));
  }

  /**
   * Emulates WireMock's {@code Fault.MALFORMED_RESPONSE_CHUNK}: every request is answered with
   * valid headers with chunked transfer encoding followed by garbage where a chunk size line
   * belongs. OkHttp's MockWebServer no longer ships a policy for malformed chunks, so this uses a
   * raw socket, like {@code getWithRequestBody()}.
   */
  private static Thread startMalformedChunkServer(ServerSocket httpServer) {
    final Thread serverThread =
        new Thread(
            () -> {
              while (true) {
                try (Socket socket = httpServer.accept()) {
                  socket.setSoTimeout(5000);
                  drainRequest(socket.getInputStream());
                  // the headers are valid, the chunk framing is not: the client reads
                  // "Malformed response chunk!" where a chunk size line belongs
                  socket
                      .getOutputStream()
                      .write(
                          ("HTTP/1.1 200 OK\r\n"
                                  + "Transfer-Encoding: chunked\r\n"
                                  + "\r\n"
                                  + "Malformed response chunk!\r\n")
                              .getBytes(UTF_8));
                  socket.getOutputStream().flush();
                  Thread.sleep(200); // keep the socket open, so the failure is parsing, not EOF
                } catch (IOException | InterruptedException e) {
                  return;
                }
              }
            });
    serverThread.start();
    return serverThread;
  }

  /** A bounded retryer that counts every attempt it handles, across all invocations. */
  private static final class CountingRetryer implements Retryer {
    private final AtomicInteger attempts;
    private int invocationAttempts;

    private CountingRetryer(AtomicInteger attempts) {
      this.attempts = attempts;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
      attempts.incrementAndGet();
      if (invocationAttempts++ >= 2) {
        throw e;
      }
      try {
        Thread.sleep(1);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw e;
      }
    }

    @Override
    public Retryer clone() {
      return new CountingRetryer(attempts);
    }
  }

  /** Reads the request head and, if present, the Content-Length framed body. */
  private static void drainRequest(InputStream in) throws IOException {
    final StringBuilder head = new StringBuilder();
    final byte[] buffer = new byte[8192];
    while (head.indexOf("\r\n\r\n") == -1) {
      final int read = in.read(buffer);
      if (read == -1) {
        return;
      }
      head.append(new String(buffer, 0, read, UTF_8));
    }
    long contentLength = 0;
    for (String line : head.substring(0, head.indexOf("\r\n\r\n")).split("\r\n")) {
      if (line.toLowerCase().startsWith("content-length:")) {
        contentLength = Long.parseLong(line.substring(line.indexOf(':') + 1).trim());
      }
    }
    long consumed = head.length() - head.indexOf("\r\n\r\n") - 4;
    final byte[] sink = new byte[8192];
    while (consumed < contentLength) {
      final int read = in.read(sink, 0, (int) Math.min(sink.length, contentLength - consumed));
      if (read == -1) {
        return;
      }
      consumed += read;
    }
  }

  @Override
  public Feign.Builder newBuilder() {
    return Feign.builder().client(new Http2Client());
  }
}
