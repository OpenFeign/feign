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
package feign.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Feign;
import feign.Param;
import feign.RequestInterceptor;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MethodInterceptorTest {

  private final MockWebServer server = new MockWebServer();

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  interface Api {
    @RequestLine("POST /things")
    String send(String body);

    @RequestLine("GET /things/{id}")
    String fetch(@Param("id") String id);
  }

  @Test
  void interceptorRunsAfterContractAndBeforeRequestInterceptor() throws Exception {
    server.enqueue(new MockResponse().setBody("ok"));

    AtomicReference<RequestTemplate> seenByMethodInterceptor = new AtomicReference<>();
    AtomicReference<RequestTemplate> seenByRequestInterceptor = new AtomicReference<>();

    MethodInterceptor methodInterceptor =
        (invocation, chain) -> {
          seenByMethodInterceptor.set(invocation.requestTemplate());
          // headers set here are visible to RequestInterceptors
          invocation.requestTemplate().header("X-From-Method", "yes");
          return chain.next(invocation);
        };
    RequestInterceptor requestInterceptor =
        template -> {
          seenByRequestInterceptor.set(template);
          assertThat(template.headers().get("X-From-Method")).contains("yes");
          template.header("X-From-Request", "yes");
        };

    Api api =
        Feign.builder()
            .methodInterceptor(methodInterceptor)
            .requestInterceptor(requestInterceptor)
            .target(Api.class, "http://localhost:" + server.getPort());

    api.send("payload");

    assertThat(seenByMethodInterceptor.get()).isNotNull();
    assertThat(seenByRequestInterceptor.get()).isNotNull();
    assertThat(server.takeRequest().getHeader("X-From-Method")).isEqualTo("yes");
  }

  @Test
  void interceptorCanShortCircuit() throws Exception {
    MethodInterceptor methodInterceptor = (invocation, chain) -> "short-circuited";

    Api api =
        Feign.builder()
            .methodInterceptor(methodInterceptor)
            .target(Api.class, "http://localhost:" + server.getPort());

    String result = api.send("ignored");

    assertThat(result).isEqualTo("short-circuited");
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void interceptorExceptionSurfacesToCaller() {
    RuntimeException boom = new IllegalStateException("nope");
    MethodInterceptor methodInterceptor =
        (invocation, chain) -> {
          throw boom;
        };

    Api api =
        Feign.builder()
            .methodInterceptor(methodInterceptor)
            .target(Api.class, "http://localhost:" + server.getPort());

    assertThatThrownBy(() -> api.send("x")).isSameAs(boom);
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void interceptorSeesResponseAfterChainCompletes() throws Exception {
    server.enqueue(new MockResponse().setHeader("ETag", "v1").setBody("payload"));

    AtomicReference<Response> captured = new AtomicReference<>();
    MethodInterceptor methodInterceptor =
        (invocation, chain) -> {
          assertThat(invocation.response()).isNull();
          Object result = chain.next(invocation);
          captured.set(invocation.response());
          return result;
        };

    Api api =
        Feign.builder()
            .methodInterceptor(methodInterceptor)
            .target(Api.class, "http://localhost:" + server.getPort());

    api.fetch("42");

    Response response = captured.get();
    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.headers().get("etag")).contains("v1");
  }

  @Test
  void chainOrderIsRegistrationOrder() throws Exception {
    server.enqueue(new MockResponse().setBody("ok"));

    List<String> visited = new ArrayList<>();
    MethodInterceptor first =
        (invocation, chain) -> {
          visited.add("first-pre");
          Object result = chain.next(invocation);
          visited.add("first-post");
          return result;
        };
    MethodInterceptor second =
        (invocation, chain) -> {
          visited.add("second-pre");
          Object result = chain.next(invocation);
          visited.add("second-post");
          return result;
        };

    Api api =
        Feign.builder()
            .methodInterceptor(first)
            .methodInterceptor(second)
            .target(Api.class, "http://localhost:" + server.getPort());

    api.fetch("1");

    assertThat(visited).containsExactly("first-pre", "second-pre", "second-post", "first-post");
  }

  @Test
  void invocationExposesArguments() throws Exception {
    server.enqueue(new MockResponse().setBody("ok"));

    AtomicReference<Object[]> seenArgs = new AtomicReference<>();
    MethodInterceptor methodInterceptor =
        (invocation, chain) -> {
          seenArgs.set(invocation.arguments());
          assertThat(invocation.method().getName()).isEqualTo("send");
          assertThat(invocation.target().type()).isEqualTo(Api.class);
          return chain.next(invocation);
        };

    Api api =
        Feign.builder()
            .methodInterceptor(methodInterceptor)
            .target(Api.class, "http://localhost:" + server.getPort());

    api.send("hello");

    assertThat(seenArgs.get()).containsExactly("hello");
  }
}
