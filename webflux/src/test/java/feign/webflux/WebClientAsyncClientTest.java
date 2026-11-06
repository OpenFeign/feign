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
package feign.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import feign.AsyncFeign;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebClientAsyncClientTest {

  interface TestApi {
    @RequestLine("GET /")
    CompletableFuture<String> get();
  }

  private MockWebServer server;
  private WebClientAsyncClient<Object> client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = new WebClientAsyncClient<>();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void sendsMethodUrlAndHeadersAndReadsResponse() throws Exception {
    server.enqueue(
        new MockResponse().setBody("hello").addHeader("X-Response-Header", "response-value"));

    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    headers.put("X-Request-Header", Collections.singletonList("request-value"));

    CompletableFuture<Response> future =
        client.execute(
            request(HttpMethod.GET, "/path?query=1", headers, null),
            new Request.Options(),
            Optional.empty());

    Response response = future.get(5, TimeUnit.SECONDS);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.headers().get("X-Response-Header")).containsExactly("response-value");
    assertThat(bodyOf(response)).isEqualTo("hello");

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("GET");
    assertThat(recorded.getPath()).isEqualTo("/path?query=1");
    assertThat(recorded.getHeader("X-Request-Header")).isEqualTo("request-value");
  }

  @Test
  void sendsBodyWithContentType() throws Exception {
    server.enqueue(new MockResponse().setBody("created"));

    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    headers.put("Content-Type", Collections.singletonList("application/json"));
    headers.put("X-Custom", Arrays.asList("one", "two"));
    byte[] body = "{\"flavor\":\"mint\"}".getBytes(StandardCharsets.UTF_8);

    Response response =
        client
            .execute(
                request(HttpMethod.POST, "/orders", headers, body),
                new Request.Options(),
                Optional.empty())
            .get(5, TimeUnit.SECONDS);

    assertThat(response.status()).isEqualTo(200);

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("POST");
    assertThat(recorded.getPath()).isEqualTo("/orders");
    assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json");
    assertThat(recorded.getHeaders().values("X-Custom")).containsExactly("one", "two");
    assertThat(recorded.getBody().readUtf8()).isEqualTo("{\"flavor\":\"mint\"}");
  }

  @Test
  void exposesErrorStatusAsRegularResponse() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    Response response =
        client
            .execute(
                request(HttpMethod.GET, "/", null, null), new Request.Options(), Optional.empty())
            .get(5, TimeUnit.SECONDS);

    assertThat(response.status()).isEqualTo(500);
    assertThat(response.reason()).isEqualTo("Internal Server Error");
    assertThat(bodyOf(response)).isEqualTo("boom");
  }

  @Test
  void completesExceptionallyWhenServerIsUnreachable() throws Exception {
    MockWebServer unreachable = new MockWebServer();
    unreachable.start();
    Request request = request(HttpMethod.GET, unreachable.url("/").toString(), null, null);
    unreachable.shutdown();

    CompletableFuture<Response> future =
        client.execute(request, new Request.Options(), Optional.empty());

    Throwable failure = failureOf(future);
    assertThat(failure).isNotNull();
    assertThat(findInChain(failure, IOException.class))
        .as("expected an IOException in the failure chain, got: %s", failure)
        .isNotNull();
  }

  @Test
  void appliesReadTimeoutFromOptions() throws Exception {
    server.enqueue(new MockResponse().setBody("slow").setBodyDelay(2, TimeUnit.SECONDS));

    Request.Options options =
        new Request.Options(1000, TimeUnit.MILLISECONDS, 10, TimeUnit.MILLISECONDS, true);

    CompletableFuture<Response> future =
        client.execute(request(HttpMethod.GET, "/", null, null), options, Optional.empty());

    Throwable failure = failureOf(future);
    assertThat(failure).isNotNull();
    assertThat(findInChain(failure, TimeoutException.class))
        .as("expected a TimeoutException in the failure chain, got: %s", failure)
        .isNotNull();
  }

  @Test
  void decodesOnTheTransportThreadInsteadOfTheCaller() throws Exception {
    server.enqueue(new MockResponse().setBody("hello"));

    AtomicReference<Thread> decodingThread = new AtomicReference<>();
    TestApi api =
        AsyncFeign.<TestApi>builder()
            .client(new WebClientAsyncClient<>())
            .decoder(
                (response, type) -> {
                  decodingThread.set(Thread.currentThread());
                  return new Decoder.Default().decode(response, type);
                })
            .target(TestApi.class, server.url("/").toString());

    Thread callerThread = Thread.currentThread();
    CompletableFuture<String> body = api.get();

    assertThat(body.get(5, TimeUnit.SECONDS)).isEqualTo("hello");
    assertThat(decodingThread.get()).isNotSameAs(callerThread);
    assertThat(decodingThread.get().getName()).contains("reactor-http");
  }

  @Test
  void errorDecoderSeesTheErrorResponse() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    TestApi api =
        AsyncFeign.<TestApi>builder()
            .client(new WebClientAsyncClient<>())
            .target(TestApi.class, server.url("/").toString());

    FeignException exception = findInChain(failureOf(api.get()), FeignException.class);

    assertThat(exception).isNotNull();
    assertThat(exception.status()).isEqualTo(500);
  }

  private Request request(
      HttpMethod method, String path, Map<String, Collection<String>> headers, byte[] body) {
    return Request.create(
        method,
        path.startsWith("http") ? path : server.url(path).toString(),
        headers == null ? Collections.emptyMap() : headers,
        body,
        StandardCharsets.UTF_8,
        null);
  }

  private static String bodyOf(Response response) throws IOException {
    return Util.toString(response.body().asReader(Util.UTF_8));
  }

  /** Returns the exception the future was completed with, failing the test if it succeeded. */
  private static Throwable failureOf(CompletableFuture<?> future) throws Exception {
    Throwable failure = future.handle((ignored, throwable) -> throwable).get(5, TimeUnit.SECONDS);
    assertThat(failure).as("expected the future to complete exceptionally").isNotNull();
    return failure;
  }

  private static <T extends Throwable> T findInChain(Throwable throwable, Class<T> type) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (type.isInstance(cause)) {
        return type.cast(cause);
      }
    }
    return null;
  }
}
