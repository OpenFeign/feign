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
package feign.optionals;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Feign;
import feign.RequestLine;
import feign.core.codec.DefaultDecoder;
import java.util.Optional;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;

class OptionalDecoderTests {

  interface OptionalInterface {
    @RequestLine("GET /")
    Optional<String> getAsOptional();

    @RequestLine("GET /")
    String get();
  }

  @Test
  void simple404OptionalTest() throws Exception {
    final MockWebServer server = new MockWebServer();

    server.start();
    server.enqueue(new MockResponse.Builder().code(404).build());
    server.enqueue(new MockResponse.Builder().body("foo").build());

    final OptionalInterface api =
        Feign.builder()
            .dismiss404()
            .decoder(new OptionalDecoder(new DefaultDecoder()))
            .target(OptionalInterface.class, server.url("/").toString());

    assertThat(api.getAsOptional()).isEmpty();
    assertThat(api.getAsOptional()).hasValue("foo");
  }

  @Test
  void simple204OptionalTest() throws Exception {
    final MockWebServer server = new MockWebServer();

    server.start();
    server.enqueue(new MockResponse.Builder().code(204).build());

    final OptionalInterface api =
        Feign.builder()
            .decoder(new OptionalDecoder(new DefaultDecoder()))
            .target(OptionalInterface.class, server.url("/").toString());

    assertThat(api.getAsOptional()).isEmpty();
  }

  @Test
  void test200WithOptionalString() throws Exception {
    final MockWebServer server = new MockWebServer();

    server.start();
    server.enqueue(new MockResponse.Builder().code(200).body("foo").build());

    final OptionalInterface api =
        Feign.builder()
            .decoder(new OptionalDecoder(new DefaultDecoder()))
            .target(OptionalInterface.class, server.url("/").toString());

    Optional<String> response = api.getAsOptional();

    assertThat(response).hasValue("foo");
  }

  @Test
  void test200WhenResponseBodyIsNull() throws Exception {
    final MockWebServer server = new MockWebServer();

    server.start();
    server.enqueue(new MockResponse.Builder().code(200).build());

    final OptionalInterface api =
        Feign.builder()
            .decoder(new OptionalDecoder(((_, _) -> null)))
            .target(OptionalInterface.class, server.url("/").toString());

    assertThat(api.getAsOptional()).isEmpty();
  }

  @Test
  void test200WhenDecodingNoOptional() throws Exception {
    final MockWebServer server = new MockWebServer();

    server.start();
    server.enqueue(new MockResponse.Builder().code(200).body("foo").build());

    final OptionalInterface api =
        Feign.builder()
            .decoder(new OptionalDecoder(new DefaultDecoder()))
            .target(OptionalInterface.class, server.url("/").toString());

    assertThat(api.get()).isEqualTo("foo");
  }
}
