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
package feign.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpCacheInterceptorTest {

  private final MockWebServer server = new MockWebServer();
  private final InMemoryHttpCacheStore store = new InMemoryHttpCacheStore();

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  interface Api {
    @RequestLine("GET /things/{id}")
    String fetch(@Param("id") String id);

    @RequestLine("POST /things")
    String create(String body);

    @RequestLine("QUERY /things")
    String query(String body);
  }

  private Api api() {
    return Feign.builder()
        .methodInterceptor(new HttpCacheInterceptor(store))
        .target(Api.class, "http://localhost:" + server.getPort());
  }

  @Test
  void firstCallStoresEntryWhenETagPresent() throws Exception {
    server.enqueue(new MockResponse().setHeader("ETag", "\"v1\"").setBody("payload-1"));

    String result = api().fetch("42");

    assertThat(result).isEqualTo("payload-1");
    assertThat(store.size()).isEqualTo(1);
    RecordedRequest first = server.takeRequest();
    assertThat(first.getHeader("If-None-Match")).isNull();
  }

  @Test
  void notModifiedReturnsCachedValueAndSendsConditionalHeader() throws Exception {
    server.enqueue(new MockResponse().setHeader("ETag", "\"v1\"").setBody("payload-1"));
    server.enqueue(new MockResponse().setResponseCode(304));

    Api api = api();
    String first = api.fetch("42");
    String second = api.fetch("42");

    assertThat(first).isEqualTo("payload-1");
    assertThat(second).isEqualTo("payload-1");
    server.takeRequest(); // first
    RecordedRequest revalidation = server.takeRequest();
    assertThat(revalidation.getHeader("If-None-Match")).isEqualTo("\"v1\"");
  }

  @Test
  void freshTwoHundredReplacesCachedEntry() throws Exception {
    server.enqueue(new MockResponse().setHeader("ETag", "\"v1\"").setBody("payload-1"));
    server.enqueue(new MockResponse().setHeader("ETag", "\"v2\"").setBody("payload-2"));
    server.enqueue(new MockResponse().setResponseCode(304));

    Api api = api();
    String first = api.fetch("42");
    String second = api.fetch("42");
    String third = api.fetch("42");

    assertThat(first).isEqualTo("payload-1");
    assertThat(second).isEqualTo("payload-2");
    assertThat(third).isEqualTo("payload-2");
    server.takeRequest();
    server.takeRequest();
    RecordedRequest third304 = server.takeRequest();
    assertThat(third304.getHeader("If-None-Match")).isEqualTo("\"v2\"");
  }

  @Test
  void responseWithoutValidatorsIsNotStored() throws Exception {
    server.enqueue(new MockResponse().setBody("payload"));

    api().fetch("42");

    assertThat(store.size()).isZero();
  }

  @Test
  void postRequestsBypassCache() throws Exception {
    server.enqueue(new MockResponse().setHeader("ETag", "\"v1\"").setBody("payload"));

    api().create("body");

    assertThat(store.size()).isZero();
    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getHeader("If-None-Match")).isNull();
  }

  @Test
  void queryRequestsParticipateInCache() {
    AtomicInteger calls = new AtomicInteger();
    Client client =
        (request, options) -> {
          calls.incrementAndGet();
          if (request.headers().containsKey("If-None-Match")) {
            return Response.builder()
                .status(304)
                .headers(Collections.emptyMap())
                .request(request)
                .build();
          }
          String body = request.body() != null ? new String(request.body(), Util.UTF_8) : "";
          Map<String, Collection<String>> headers = new HashMap<>();
          headers.put("ETag", Collections.singletonList("\"" + body + "\""));
          return Response.builder()
              .status(200)
              .headers(headers)
              .body("result-" + body, Util.UTF_8)
              .request(request)
              .build();
        };

    Api api =
        Feign.builder()
            .client(client)
            .methodInterceptor(new HttpCacheInterceptor(store))
            .target(Api.class, "http://localhost:0");

    String first = api.query("q1");
    String second = api.query("q2");
    String third = api.query("q1");

    assertThat(first).isEqualTo("result-q1");
    assertThat(second).isEqualTo("result-q2");
    assertThat(third).isEqualTo("result-q1");
    assertThat(calls.get()).isEqualTo(3);
    assertThat(store.size()).isEqualTo(2);
  }

  @Test
  void noStoreCacheControlPreventsStorage() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("ETag", "\"v1\"")
            .setHeader("Cache-Control", "no-store")
            .setBody("payload"));

    api().fetch("42");

    assertThat(store.size()).isZero();
  }

  @Test
  void serverErrorPropagatesAndDoesNotEvictExistingEntry() throws Exception {
    server.enqueue(new MockResponse().setHeader("ETag", "\"v1\"").setBody("payload-1"));
    server.enqueue(new MockResponse().setResponseCode(500).setBody("nope"));

    Api api = api();
    String first = api.fetch("42");
    assertThatThrownBy(() -> api.fetch("42")).isInstanceOf(FeignException.class);

    assertThat(first).isEqualTo("payload-1");
    assertThat(store.size()).isEqualTo(1);
  }

  @Test
  void lastModifiedRevalidationSendsIfModifiedSince() throws Exception {
    String lastModified = "Wed, 21 Oct 2020 07:28:00 GMT";
    server.enqueue(new MockResponse().setHeader("Last-Modified", lastModified).setBody("payload"));
    server.enqueue(new MockResponse().setResponseCode(304));

    Api api = api();
    api.fetch("42");
    String second = api.fetch("42");

    assertThat(second).isEqualTo("payload");
    server.takeRequest();
    RecordedRequest revalidation = server.takeRequest();
    assertThat(revalidation.getHeader("If-Modified-Since")).isEqualTo(lastModified);
  }
}
