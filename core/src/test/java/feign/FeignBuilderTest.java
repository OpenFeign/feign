/**
 * Copyright 2012-2018 The Feign Authors
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

import java.util.HashMap;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.data.MapEntry;
import org.junit.Rule;
import org.junit.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import feign.codec.Decoder;
import feign.codec.Encoder;
import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FeignBuilderTest {

  @Rule
  public final MockWebServer server = new MockWebServer();

  @Test
  public void testDefaults() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    Response response = api.codecPost("request data");
    assertEquals("response data", Util.toString(response.body().asReader()));

    assertThat(server.takeRequest())
        .hasBody("request data");
  }

  /** Shows exception handling isn't required to coerce 404 to null or empty */
  @Test
  public void testDecode404() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setResponseCode(400));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().decode404().target(TestInterface.class, url);

    assertThat(api.getQueues("/")).isEmpty(); // empty, not null!
    assertThat(api.decodedPost()).isNull(); // null, not empty!

    try { // ensure other 400 codes are not impacted.
      api.decodedPost();
      failBecauseExceptionWasNotThrown(FeignException.class);
    } catch (FeignException e) {
      assertThat(e.status()).isEqualTo(400);
    }
  }



  @Test
  public void testNoFollowRedirect() {
    server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/"));

    String url = "http://localhost:" + server.getPort();
    TestInterface noFollowApi = Feign.builder()
        .options(new Request.Options(100, 600, false))
        .target(TestInterface.class, url);

    Response response = noFollowApi.defaultMethodPassthrough();
    assertThat(response.status()).isEqualTo(302);
    assertThat(response.headers().getOrDefault("Location", null))
        .isNotNull()
        .isEqualTo(Collections.singletonList("/"));

    server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/"));
    server.enqueue(new MockResponse().setResponseCode(200));
    TestInterface defaultApi = Feign.builder()
        .options(new Request.Options(100, 600, true))
        .target(TestInterface.class, url);
    assertThat(defaultApi.defaultMethodPassthrough().status()).isEqualTo(200);
  }


  @Test
  public void testUrlPathConcatUrlTrailingSlash() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort() + "/";
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    api.codecPost("request data");
    assertThat(server.takeRequest()).hasPath("/");
  }

  @Test
  public void testUrlPathConcatNoPathOnRequestLine() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort() + "/";
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    api.getNoPath();
    assertThat(server.takeRequest()).hasPath("/");
  }

  @Test
  public void testHttpNotFoundError() {
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
  public void testUrlPathConcatNoInitialSlashOnPath() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort() + "/";
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    api.getNoInitialSlashOnSlash();
    assertThat(server.takeRequest()).hasPath("/api/thing");
  }

  @Test
  public void testUrlPathConcatNoInitialSlashOnPathNoTrailingSlashOnUrl() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    api.getNoInitialSlashOnSlash();
    assertThat(server.takeRequest()).hasPath("/api/thing");
  }

  @Test
  public void testOverrideEncoder() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    Encoder encoder = new Encoder() {
      @Override
      public void encode(Object object, Type bodyType, RequestTemplate template) {
        template.body(object.toString());
      }
    };

    TestInterface api = Feign.builder().encoder(encoder).target(TestInterface.class, url);
    api.encodedPost(Arrays.asList("This", "is", "my", "request"));

    assertThat(server.takeRequest())
        .hasBody("[This, is, my, request]");
  }

  @Test
  public void testOverrideDecoder() throws Exception {
    server.enqueue(new MockResponse().setBody("success!"));

    String url = "http://localhost:" + server.getPort();
    Decoder decoder = new Decoder() {
      @Override
      public Object decode(Response response, Type type) {
        return "fail";
      }
    };

    TestInterface api = Feign.builder().decoder(decoder).target(TestInterface.class, url);
    assertEquals("fail", api.decodedPost());

    assertEquals(1, server.getRequestCount());
  }

  @Test
  public void testOverrideQueryMapEncoder() throws Exception {
    server.enqueue(new MockResponse());

    String url = "http://localhost:" + server.getPort();
    QueryMapEncoder customMapEncoder = new QueryMapEncoder() {
      @Override
      public Map<String, Object> encode(Object ignored) {
        Map<String, Object> queryMap = new HashMap<String, Object>();
        queryMap.put("key1", "value1");
        queryMap.put("key2", "value2");
        return queryMap;
      }
    };

    TestInterface api =
        Feign.builder().queryMapEncoder(customMapEncoder).target(TestInterface.class, url);
    api.queryMapEncoded("ignored");

    assertThat(server.takeRequest()).hasQueryParams(Arrays.asList("key1=value1", "key2=value2"));
    assertEquals(1, server.getRequestCount());
  }

  @Test
  public void testProvideRequestInterceptors() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    RequestInterceptor requestInterceptor = new RequestInterceptor() {
      @Override
      public void apply(RequestTemplate template) {
        template.header("Content-Type", "text/plain");
      }
    };

    TestInterface api =
        Feign.builder().requestInterceptor(requestInterceptor).target(TestInterface.class, url);
    Response response = api.codecPost("request data");
    assertEquals(Util.toString(response.body().asReader()), "response data");

    assertThat(server.takeRequest())
        .hasHeaders(MapEntry.entry("Content-Type", Collections.singletonList("text/plain")))
        .hasBody("request data");
  }

  @Test
  public void testProvideInvocationHandlerFactory() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();

    final AtomicInteger callCount = new AtomicInteger();
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
    assertEquals("response data", Util.toString(response.body().asReader()));
    assertEquals(1, callCount.get());

    assertThat(server.takeRequest())
        .hasBody("request data");
  }

  @Test
  public void testSlashIsEncodedInPathParams() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();

    TestInterface api = Feign.builder().target(TestInterface.class, url);
    api.getQueues("/");

    assertThat(server.takeRequest())
        .hasPath("/api/queues/%2F");
  }

  @Test
  public void testBasicDefaultMethod() throws Exception {
    String url = "http://localhost:" + server.getPort();

    TestInterface api = Feign.builder().target(TestInterface.class, url);
    String result = api.independentDefaultMethod();

    assertThat(result.equals("default result"));
  }

  @Test
  public void testDefaultCallingProxiedMethod() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    String url = "http://localhost:" + server.getPort();
    TestInterface api = Feign.builder().target(TestInterface.class, url);

    Response response = api.defaultMethodPassthrough();
    assertEquals("response data", Util.toString(response.body().asReader()));
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
   * @throws Exception
   */
  @Test
  public void testDoNotCloseAfterDecode() throws Exception {
    server.enqueue(new MockResponse().setBody("success!"));

    String url = "http://localhost:" + server.getPort();
    Decoder decoder = new Decoder() {
      @Override
      public Iterator decode(Response response, Type type) {
        return new Iterator() {
          private boolean called = false;

          @Override
          public boolean hasNext() {
            return !called;
          }

          @Override
          public Object next() {
            try {
              return Util.toString(response.body().asReader());
            } catch (IOException e) {
              fail(e.getMessage());
              return null;
            } finally {
              Util.ensureClosed(response);
              called = true;
            }
          }
        };
      }
    };

    TestInterface api = Feign.builder()
        .decoder(decoder)
        .doNotCloseAfterDecode()
        .target(TestInterface.class, url);
    Iterator<String> iterator = api.decodedLazyPost();

    assertTrue(iterator.hasNext());
    assertEquals("success!", iterator.next());
    assertFalse(iterator.hasNext());

    assertEquals(1, server.getRequestCount());
  }

  /**
   * When {@link Feign.Builder#doNotCloseAfterDecode()} is enabled an an exception is thrown from
   * the {@link Decoder}, the response should be closed.
   */
  @Test
  public void testDoNotCloseAfterDecodeDecoderFailure() throws Exception {
    server.enqueue(new MockResponse().setBody("success!"));

    String url = "http://localhost:" + server.getPort();
    Decoder angryDecoder = new Decoder() {
      @Override
      public Object decode(Response response, Type type) throws IOException {
        throw new IOException("Failed to decode the response");
      }
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

                  @Override
                  public Reader asReader() throws IOException {
                    return original.body().asReader();
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
    assertTrue("Responses must be closed when the decoder fails", closed.get());
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

    @RequestLine(value = "GET /api/queues/{vhost}", decodeSlash = false)
    byte[] getQueues(@Param("vhost") String vhost);

    default String independentDefaultMethod() {
      return "default result";
    }

    default Response defaultMethodPassthrough() {
      return getNoPath();
    }
  }
}
