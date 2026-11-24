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
package feign.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.AsyncFeign;
import feign.Feign;
import feign.FeignException;
import feign.Param;
import feign.RequestLine;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class MockClientEncodedUrlTest {

  interface Files {

    @RequestLine(value = "GET /files/{path}", decodeSlash = false)
    String file(@Param("path") String path);

    @RequestLine("GET /files/{dir}/{name}")
    String nestedFile(@Param("dir") String dir, @Param("name") String name);
  }

  interface AsyncFiles {

    @RequestLine(value = "GET /files/{path}", decodeSlash = false)
    CompletableFuture<String> file(@Param("path") String path);
  }

  private static Files files(MockClient mockClient) {
    return Feign.builder().client(mockClient).target(new MockTarget<>(Files.class));
  }

  @Test
  void decodesRequestUrlsByDefault() {
    MockClient mockClient = new MockClient().ok(HttpMethod.GET, "/files/a/b", "decoded");

    assertThat(files(mockClient).file("a/b")).isEqualTo("decoded");
    assertThat(mockClient.verifyOne(HttpMethod.GET, "/files/a/b").url()).isEqualTo("/files/a%2Fb");
  }

  @Test
  void matchesRequestUrlsAsSentWhenEnabled() {
    MockClient mockClient =
        new MockClient()
            .matchEncodedUrls()
            .ok(HttpMethod.GET, "/files/a%2Fb", "encoded")
            .ok(HttpMethod.GET, "/files/a/b", "nested");
    Files files = files(mockClient);

    assertThat(files.file("a/b")).isEqualTo("encoded");
    assertThat(files.nestedFile("a", "b")).isEqualTo("nested");
    mockClient.verifyOne(HttpMethod.GET, "/files/a%2Fb");
    mockClient.verifyOne(HttpMethod.GET, "/files/a/b");
  }

  @Test
  void doesNotMatchDecodedUrlWhenEnabled() {
    MockClient mockClient =
        new MockClient().matchEncodedUrls().ok(HttpMethod.GET, "/files/a/b", "nested");

    assertThatThrownBy(() -> files(mockClient).file("a/b"))
        .isInstanceOf(FeignException.NotFound.class);
    mockClient.verifyOne(HttpMethod.GET, "/files/a%2Fb");
    mockClient.verifyNever(HttpMethod.GET, "/files/a/b");
  }

  @Test
  void verifiesWithRequestKeyWhenEnabled() {
    RequestKey encoded = RequestKey.builder(HttpMethod.GET, "/files/a%2Fb").build();
    MockClient mockClient = new MockClient().matchEncodedUrls().ok(encoded, "encoded");
    Files files = files(mockClient);

    files.file("a/b");
    files.file("a/b");

    assertThat(mockClient.verifyTimes(encoded, 2)).hasSize(2);
    mockClient.verifyNever(RequestKey.builder(HttpMethod.GET, "/files/a/b").build());
  }

  @Test
  void matchesRequestUrlsAsSentInSequentialMode() {
    MockClient mockClient =
        new MockClient(true)
            .matchEncodedUrls()
            .ok(HttpMethod.GET, "/files/a%2Fb", "encoded")
            .ok(HttpMethod.GET, "/files/a/b", "nested");
    Files files = files(mockClient);

    assertThat(files.file("a/b")).isEqualTo("encoded");
    assertThat(files.nestedFile("a", "b")).isEqualTo("nested");
    mockClient.verifyStatus();
  }

  @Test
  void matchesRequestUrlsAsSentWithAsyncFeign() throws Exception {
    MockClient mockClient =
        new MockClient().matchEncodedUrls().ok(HttpMethod.GET, "/files/a%2Fb", "encoded");
    AsyncFiles files =
        AsyncFeign.<Object>builder().client(mockClient).target(new MockTarget<>(AsyncFiles.class));

    assertThat(files.file("a/b").get()).isEqualTo("encoded");
    mockClient.verifyOne(HttpMethod.GET, "/files/a%2Fb");
  }
}
