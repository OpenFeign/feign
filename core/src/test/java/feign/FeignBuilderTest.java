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

import static org.testng.Assert.assertEquals;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

public class FeignBuilderTest {
  interface TestInterface {
    @RequestLine("POST /")
    Response codecPost(String data);

    @RequestLine("POST /")
    void encodedPost(List<String> data);

    @RequestLine("POST /")
    String decodedPost();
  }

  @Test
  public void testDefaults() throws Exception {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("response data"));
    server.play();

    String url = "http://localhost:" + server.getPort();
    try {
      TestInterface api = Feign.builder().target(TestInterface.class, url);
      Response response = api.codecPost("request data");
      assertEquals(Util.toString(response.body().asReader()), "response data");
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
      assertEquals(server.takeRequest().getUtf8Body(), "request data");
    }
  }

  @Test
  public void testOverrideEncoder() throws Exception {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("response data"));
    server.play();

    String url = "http://localhost:" + server.getPort();
    Encoder encoder =
        new Encoder() {
          @Override
          public void encode(Object object, RequestTemplate template) throws EncodeException {
            template.body(object.toString());
          }
        };
    try {
      TestInterface api = Feign.builder().encoder(encoder).target(TestInterface.class, url);
      api.encodedPost(Arrays.asList("This", "is", "my", "request"));
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
      assertEquals(server.takeRequest().getUtf8Body(), "[This, is, my, request]");
    }
  }

  @Test
  public void testOverrideDecoder() throws Exception {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("success!"));
    server.play();

    String url = "http://localhost:" + server.getPort();
    Decoder decoder =
        new Decoder() {
          @Override
          public Object decode(Response response, Type type) {
            return "fail";
          }
        };

    try {
      TestInterface api = Feign.builder().decoder(decoder).target(TestInterface.class, url);
      assertEquals(api.decodedPost(), "fail");
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
    }
  }

  @Test
  public void testProvideRequestInterceptors() throws Exception {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("response data"));
    server.play();

    String url = "http://localhost:" + server.getPort();
    RequestInterceptor requestInterceptor =
        new RequestInterceptor() {
          @Override
          public void apply(RequestTemplate template) {
            template.header("Content-Type", "text/plain");
          }
        };
    try {
      TestInterface api =
          Feign.builder().requestInterceptor(requestInterceptor).target(TestInterface.class, url);
      Response response = api.codecPost("request data");
      assertEquals(Util.toString(response.body().asReader()), "response data");
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
      RecordedRequest request = server.takeRequest();
      assertEquals(request.getUtf8Body(), "request data");
      assertEquals(request.getHeader("Content-Type"), "text/plain");
    }
  }

  @Test
  public void testProvideInvocationHandlerFactory() throws Exception {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("response data"));
    server.play();

    String url = "http://localhost:" + server.getPort();

    final AtomicInteger callCount = new AtomicInteger();
    InvocationHandlerFactory factory =
        new InvocationHandlerFactory() {
          private final InvocationHandlerFactory delegate = new Default();

          @Override
          public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
            callCount.incrementAndGet();
            return delegate.create(target, dispatch);
          }
        };

    try {
      TestInterface api =
          Feign.builder().invocationHandlerFactory(factory).target(TestInterface.class, url);
      Response response = api.codecPost("request data");
      assertEquals(Util.toString(response.body().asReader()), "response data");
      assertEquals(callCount.get(), 1);
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
      RecordedRequest request = server.takeRequest();
      assertEquals(request.getUtf8Body(), "request data");
    }
  }
}
