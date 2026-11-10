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
package feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;

/**
 * @author pengfei.zhao
 */
@SuppressWarnings("deprecation")
class OptionsTest {

  interface OptionsInterface {
    @RequestLine("GET /")
    String get(Request.Options options);

    @RequestLine("GET /")
    String get();
  }

  @Test
  void socketTimeoutTest() throws IOException {
    final MockWebServer server = new MockWebServer();

    server.start();
    server.enqueue(new MockResponse.Builder().body("foo").bodyDelay(3, TimeUnit.SECONDS).build());

    final OptionsInterface api =
        Feign.builder()
            .options(new Request.Options(1000, 1000))
            .target(OptionsInterface.class, server.url("/").toString());

    FeignException exception =
        assertThatExceptionOfType(FeignException.class).isThrownBy(() -> api.get()).actual();
    assertThat(exception).hasCauseInstanceOf(SocketTimeoutException.class);
  }

  @Test
  void normalResponseTest() throws IOException {
    final MockWebServer server = new MockWebServer();

    server.start();
    server.enqueue(new MockResponse.Builder().body("foo").bodyDelay(3, TimeUnit.SECONDS).build());

    final OptionsInterface api =
        Feign.builder()
            .options(new Request.Options(1000, 1000))
            .target(OptionsInterface.class, server.url("/").toString());

    assertThat(api.get(new Request.Options(1000, 4 * 1000))).isEqualTo("foo");
  }

  @Test
  void optionsAreValues() {
    Request.Options millis =
        new Request.Options(1000, TimeUnit.MILLISECONDS, 2, TimeUnit.SECONDS, true);
    Request.Options seconds =
        new Request.Options(1, TimeUnit.SECONDS, 2000, TimeUnit.MILLISECONDS, true);

    assertThat(millis).isEqualTo(seconds).hasSameHashCodeAs(seconds);
    assertThat(millis)
        .isNotEqualTo(new Request.Options(1000, TimeUnit.MILLISECONDS, 2, TimeUnit.SECONDS, false));
  }
}
