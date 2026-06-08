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

import feign.Feign;
import feign.FeignException;
import feign.Param;
import feign.RequestLine;
import java.io.IOException;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpCacheInterceptorTest {

  private final MockWebServer server = new MockWebServer();
  private final InMemoryHttpCacheStore store = new InMemoryHttpCacheStore();

  @BeforeEach
  void setUp() throws IOException {
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.close();
  }

  interface Api {
    @RequestLine("GET /things/{id}")
    String fetch(@Param("id") String id);

    @RequestLine("POST /things")
    String create(String body);
  }

  private Api api() {
    return Feign.builder()
        .methodInterceptor(new HttpCacheInterceptor(store))
        .target(Api.class, "http://localhost:" + server.getPort());
  }

  @Test
  void firstCallStoresEntryWhenETagPresent() throws Exception {
    server.enqueue(
        new MockResponse.Builder().setHeader("ETag", "\"v1\"").body("payload-1").build());

    String result = api().fetch("42");

    assertThat(result).isEqualTo("payload-1");
    assertThat(store.size()).isOne();
    RecordedRequest first = server.takeRequest();
    assertThat(first.getHeaders().get("If-None-Match")).isNull();
  }

  @Test
  void notModifiedReturnsCachedValueAndSendsConditionalHeader() throws Exception {
    server.enqueue(
        new MockResponse.Builder().setHeader("ETag", "\"v1\"").body("payload-1").build());
    server.enqueue(new MockResponse.Builder().code(304).build());

    Api api = api();
    String first = api.fetch("42");
    String second = api.fetch("42");

    assertThat(first).isEqualTo("payload-1");
    assertThat(second).isEqualTo("payload-1");
    server.takeRequest(); // first
    RecordedRequest revalidation = server.takeRequest();
    assertThat(revalidation.getHeaders().get("If-None-Match")).isEqualTo("\"v1\"");
  }

  @Test
  void freshTwoHundredReplacesCachedEntry() throws Exception {
    server.enqueue(
        new MockResponse.Builder().setHeader("ETag", "\"v1\"").body("payload-1").build());
    server.enqueue(
        new MockResponse.Builder().setHeader("ETag", "\"v2\"").body("payload-2").build());
    server.enqueue(new MockResponse.Builder().code(304).build());

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
    assertThat(third304.getHeaders().get("If-None-Match")).isEqualTo("\"v2\"");
  }

  @Test
  void responseWithoutValidatorsIsNotStored() throws Exception {
    server.enqueue(new MockResponse.Builder().body("payload").build());

    api().fetch("42");

    assertThat(store.size()).isZero();
  }

  @Test
  void postRequestsBypassCache() throws Exception {
    server.enqueue(new MockResponse.Builder().setHeader("ETag", "\"v1\"").body("payload").build());

    api().create("body");

    assertThat(store.size()).isZero();
    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getHeaders().get("If-None-Match")).isNull();
  }

  @Test
  void noStoreCacheControlPreventsStorage() throws Exception {
    server.enqueue(
        new MockResponse.Builder()
            .setHeader("ETag", "\"v1\"")
            .setHeader("Cache-Control", "no-store")
            .body("payload")
            .build());

    api().fetch("42");

    assertThat(store.size()).isZero();
  }

  @Test
  void serverErrorPropagatesAndDoesNotEvictExistingEntry() throws Exception {
    server.enqueue(
        new MockResponse.Builder().setHeader("ETag", "\"v1\"").body("payload-1").build());
    server.enqueue(new MockResponse.Builder().code(500).body("nope").build());

    Api api = api();
    String first = api.fetch("42");
    assertThatThrownBy(() -> api.fetch("42")).isInstanceOf(FeignException.class);

    assertThat(first).isEqualTo("payload-1");
    assertThat(store.size()).isOne();
  }

  @Test
  void lastModifiedRevalidationSendsIfModifiedSince() throws Exception {
    String lastModified = "Wed, 21 Oct 2020 07:28:00 GMT";
    server.enqueue(
        new MockResponse.Builder()
            .setHeader("Last-Modified", lastModified)
            .body("payload")
            .build());
    server.enqueue(new MockResponse.Builder().code(304).build());

    Api api = api();
    api.fetch("42");
    String second = api.fetch("42");

    assertThat(second).isEqualTo("payload");
    server.takeRequest();
    RecordedRequest revalidation = server.takeRequest();
    assertThat(revalidation.getHeaders().get("If-Modified-Since")).isEqualTo(lastModified);
  }
}
