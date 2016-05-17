/*
 * Copyright 2013 Netflix, Inc.
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
package feign;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import feign.codec.Decoder;
import feign.codec.Encoder;

import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assert.assertEquals;
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
        .hasHeaders("Content-Type: text/plain")
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

  interface TestInterface {
    @RequestLine("GET")
    Response getNoPath();

    @RequestLine("GET api/thing")
    Response getNoInitialSlashOnSlash();

    @RequestLine("POST /")
    Response codecPost(String data);

    @RequestLine("POST /")
    void encodedPost(List<String> data);

    @RequestLine("POST /")
    String decodedPost();
    
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
