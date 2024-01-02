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
package feign;

import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import feign.codec.Decoder;
import feign.codec.Encoder;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

public class FeignBuilderTest {

  public final MockWebServer server = new MockWebServer();

  @Test
  void defaults() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    Response response = api.codecPost("request data");
    assertThat(Util.toString(response.body().asReader(Util.UTF_8))).isEqualTo("response data");

    assertThat(server.takeRequest())
        .hasBody("request data");
  }

  /** Shows exception handling isn't required to coerce 404 to null or empty */
  @Test
  void dismiss404() {
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setResponseCode(400));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().dismiss404().target(TestInterface.class, url);

    assertThat(api.getQueues("/")).isEmpty(); // empty, not null!
    assertThat(api.decodedLazyPost().hasNext()).isFalse(); // empty, not null!
    assertThat(api.optionalContent()).isEmpty(); // empty, not null!
    assertThat(api.streamPost()).isEmpty(); // empty, not null!
    assertThat(api.decodedPost()).isNull(); // null, not empty!

    try { // ensure other 400 codes are not impacted.
      api.decodedPost();
      failBecauseExceptionWasNotThrown(FeignException.class);
    } catch (FeignException e) {
      assertThat(e.status()).isEqualTo(400);
    }
  }

  /** Shows exception handling isn't required to coerce 204 to null or empty */
  @Test
  void decode204() {
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse().setResponseCode(400));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    assertThat(api.getQueues("/")).isEmpty(); // empty, not null!
    assertThat(api.decodedLazyPost().hasNext()).isFalse(); // empty, not null!
    assertThat(api.optionalContent()).isEmpty(); // empty, not null!
    assertThat(api.streamPost()).isEmpty(); // empty, not null!
    assertThat(api.decodedPost()).isNull(); // null, not empty!

    try { // ensure other 400 codes are not impacted.
      api.decodedPost();
      failBecauseExceptionWasNotThrown(FeignException.class);
    } catch (FeignException e) {
      assertThat(e.status()).isEqualTo(400);
    }
  }


  @Test
  void noFollowRedirect() {
    server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/"));

    String url = "http://localhost:" + server.getPort();
    TestInterface noFollowApi = Feign.builder()
        .options(new Request.Options(100, TimeUnit.MILLISECONDS, 600, TimeUnit.MILLISECONDS, false))
        .target(TestInterface.class, url);

    Response response = noFollowApi.defaultMethodPassthrough();
    assertThat(response.status()).isEqualTo(302);
    assertThat(response.headers()).hasEntrySatisfying("Location", value -> {
      assertThat(value).contains("/");
    });

    server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/"));
    server.enqueue(new MockResponse().setResponseCode(200));
    TestInterface defaultApi = Feign.builder()
        .options(new Request.Options(100, TimeUnit.MILLISECONDS, 600, TimeUnit.MILLISECONDS, true))
        .target(TestInterface.class, url);
    assertThat(defaultApi.defaultMethodPassthrough().status()).isEqualTo(200);
  }


  @Test
  void urlPathConcatUrlTrailingSlash() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort() + "/";
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    api.codecPost("request data");
    assertThat(server.takeRequest()).hasPath("/");
  }

  @Test
  void urlPathConcatNoPathOnRequestLine() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort() + "/";
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    api.getNoPath();
    assertThat(server.takeRequest()).hasPath("/");
  }

  @Test
  void httpNotFoundError() {
    server.enqueue(new MockResponse().setResponseCode(404));

    String url = "http://localhost:" + server.getPort() + "/";
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    try {
      api.getBodyAsString();
      failBecauseExceptionWasNotThrown(FeignException.class);
    } catch (FeignException.NotFound e) {
      assertThat(e.status()).isEqualTo(404);
    }

  }

  @Test
  void urlPathConcatNoInitialSlashOnPath() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort() + "/";
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    api.getNoInitialSlashOnSlash();
    assertThat(server.takeRequest()).hasPath("/api/thing");
  }

  @Test
  void urlPathConcatNoInitialSlashOnPathNoTrailingSlashOnUrl() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    api.getNoInitialSlashOnSlash();
    assertThat(server.takeRequest()).hasPath("/api/thing");
  }

  @Test
  void overrideEncoder() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    Encoder encoder = (object, bodyType, template) -> template.body(object.toString());

    TestInterface api = Feign.builder().encoder(encoder).target(TestInterface.class, url);
    api.encodedPost(Arrays.asList("This", "is", "my", "request"));

    assertThat(server.takeRequest())
        .hasBody("[This, is, my, request]");
  }

  @Test
  void overrideDecoder() {
    server.enqueue(new MockResponse().setBody("success!"));

    String url = "http://localhost:" + server.getPort();
    Decoder decoder = (response, type) -> "fail";

    TestInterface api = Feign.builder().decoder(decoder).target(TestInterface.class, url);
    assertThat(api.decodedPost()).isEqualTo("fail");

    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void overrideQueryMapEncoder() throws Exception {
    server.enqueue(new MockResponse());

    String url = "http://localhost:" + server.getPort();
    QueryMapEncoder customMapEncoder = ignored -> {
      Map<String, Object> queryMap = new HashMap<>();
      queryMap.put("key1", "value1");
      queryMap.put("key2", "value2");
      return queryMap;
    };

    TestInterface api =
        Feign.builder().queryMapEncoder(customMapEncoder).target(TestInterface.class, url);
    api.queryMapEncoded("ignored");

    assertThat(server.takeRequest()).hasQueryParams(Arrays.asList("key1=value1", "key2=value2"));
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void provideRequestInterceptors() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    RequestInterceptor requestInterceptor =
        template -> template.header("Content-Type", "text/plain");

    TestInterface api =
        Feign.builder().requestInterceptor(requestInterceptor).target(TestInterface.class, url);
    Response response = api.codecPost("request data");
    assertThat("response data").isEqualTo(Util.toString(response.body().asReader(Util.UTF_8)));

    assertThat(server.takeRequest())
        .hasHeaders(MapEntry.entry("Content-Type", Collections.singletonList("text/plain")))
        .hasBody("request data");
  }

  @Test
  void provideInvocationHandlerFactory() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();

    final AtomicInteger callCount = new AtomicInteger();
    // noinspection rawtypes
    InvocationHandlerFactory factory = new InvocationHandlerFactory() {
      private final InvocationHandlerFactory delegate = new Default();

      @Override
      public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
        callCount.incrementAndGet();
        return delegate.create(target, dispatch);
      }
    };

    TestInterface api =
        Feign.builder().invocationHandlerFactory(factory).target(TestInterface.class, url);
    Response response = api.codecPost("request data");
    assertThat(Util.toString(response.body().asReader(Util.UTF_8))).isEqualTo("response data");
    assertThat(callCount.get()).isEqualTo(1);

    assertThat(server.takeRequest())
        .hasBody("request data");
  }

  @Test
  void slashIsEncodedInPathParams() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();

    TestInterface api = Feign.builder().target(TestInterface.class, url);
    api.getQueues("/");

    assertThat(server.takeRequest())
        .hasPath("/api/queues/%2F");
  }

  @Test
  void basicDefaultMethod() {
    String url = "http://localhost:" + server.getPort();

    TestInterface api = Feign.builder().target(TestInterface.class, url);
    String result = api.independentDefaultMethod();

    assertThat(result.equals("default result")).isTrue();
  }

  @Test
  void defaultCallingProxiedMethod() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    Response response = api.defaultMethodPassthrough();
    assertThat(Util.toString(response.body().asReader(Util.UTF_8))).isEqualTo("response data");
    assertThat(server.takeRequest()).hasPath("/");
  }

  /**
   * This test ensures that the doNotCloseAfterDecode flag functions.
   *
   * It does so by creating a custom Decoder that lazily retrieves the response body when asked for
   * it and pops the value into an Iterator.
   *
   * Without the doNoCloseAfterDecode flag, the test will fail with a "stream is closed" exception.
   *
   */
  @Test
  void doNotCloseAfterDecode() {
    server.enqueue(new MockResponse().setBody("success!"));

    String url = "http://localhost:" + server.getPort();
    Decoder decoder = (response, type) -> new Iterator<>() {
      private boolean called = false;

      @Override
      public boolean hasNext() {
        return !called;
      }

      @Override
      public Object next() {
        try {
          return Util.toString(response.body().asReader(Util.UTF_8));
        } catch (IOException e) {
          fail("", e.getMessage());
          return null;
        } finally {
          Util.ensureClosed(response);
          called = true;
        }
      }
    };

    TestInterface api = Feign.builder()
        .decoder(decoder)
        .doNotCloseAfterDecode()
        .target(TestInterface.class, url);
    Iterator<String> iterator = api.decodedLazyPost();

    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo("success!");
    assertThat(iterator.hasNext()).isFalse();

    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  /**
   * When {@link Feign.Builder#doNotCloseAfterDecode()} is enabled an an exception is thrown from
   * the {@link Decoder}, the response should be closed.
   */
  @Test
  void doNotCloseAfterDecodeDecoderFailure() {
    server.enqueue(new MockResponse().setBody("success!"));

    String url = "http://localhost:" + server.getPort();
    Decoder angryDecoder = (response, type) -> {
      throw new IOException("Failed to decode the response");
    };

    final AtomicBoolean closed = new AtomicBoolean();
    TestInterface api = Feign.builder()
        .client(new Client() {
          Client client = new Client.Default(null, null);

          @Override
          public Response execute(Request request, Request.Options options) throws IOException {
            final Response original = client.execute(request, options);
            return Response.builder()
                .status(original.status())
                .headers(original.headers())
                .reason(original.reason())
                .request(original.request())
                .body(new Response.Body() {
                  @Override
                  public Integer length() {
                    return original.body().length();
                  }

                  @Override
                  public boolean isRepeatable() {
                    return original.body().isRepeatable();
                  }

                  @Override
                  public InputStream asInputStream() throws IOException {
                    return original.body().asInputStream();
                  }

                  @SuppressWarnings("deprecation")
                  @Override
                  public Reader asReader() throws IOException {
                    return original.body().asReader(Util.UTF_8);
                  }

                  @Override
                  public Reader asReader(Charset charset) throws IOException {
                    return original.body().asReader(charset);
                  }

                  @Override
                  public void close() throws IOException {
                    closed.set(true);
                    original.body().close();
                  }
                })
                .build();
          }
        })
        .decoder(angryDecoder)
        .doNotCloseAfterDecode()
        .target(TestInterface.class, url);
    try {
      api.decodedLazyPost();
      fail("Expected an exception");
    } catch (FeignException expected) {
    }
    assertThat(closed.get()).as("Responses must be closed when the decoder fails").isTrue();
  }

  interface TestInterface {
    @RequestLine("GET")
    Response getNoPath();

    @RequestLine("GET api/thing")
    Response getNoInitialSlashOnSlash();

    @RequestLine("GET api/thing")
    String getBodyAsString();

    @RequestLine(value = "GET /api/querymap/object")
    String queryMapEncoded(@QueryMap Object object);

    @RequestLine("POST /")
    Response codecPost(String data);

    @RequestLine("POST /")
    void encodedPost(List<String> data);

    @RequestLine("POST /")
    String decodedPost();

    @RequestLine("POST /")
    Iterator<String> decodedLazyPost();

    @RequestLine("POST /")
    Optional<String> optionalContent();

    @RequestLine("POST /")
    Stream<String> streamPost();

    @RequestLine(value = "GET /api/queues/{vhost}", decodeSlash = false)
    byte[] getQueues(@Param("vhost") String vhost);

    default String independentDefaultMethod() {
      return "default result";
    }

    default Response defaultMethodPassthrough() {
      return getNoPath();
    }
  }

  @AfterEach
  void afterEachTest() throws IOException {
    server.close();
  }
}
