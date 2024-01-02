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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

/**
 * @author pengfei.zhao
 */
@SuppressWarnings("deprecation")
class OptionsTest {

  static class ChildOptions extends Request.Options {
    public ChildOptions(int connectTimeoutMillis, int readTimeoutMillis) {
      super(connectTimeoutMillis, readTimeoutMillis);
    }
  }

  interface OptionsInterface {
    @RequestLine("GET /")
    String get(Request.Options options);

    @RequestLine("POST /")
    String getChildOptions(ChildOptions options);

    @RequestLine("GET /")
    String get();
  }

  @Test
  void socketTimeoutTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api = Feign.builder().options(new Request.Options(1000, 1000))
        .target(OptionsInterface.class, server.url("/").toString());

    FeignException exception = assertThrows(FeignException.class, () -> api.get());
    assertThat(exception).hasCauseInstanceOf(SocketTimeoutException.class);
  }

  @Test
  void normalResponseTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api = Feign.builder().options(new Request.Options(1000, 1000))
        .target(OptionsInterface.class, server.url("/").toString());

    assertThat(api.get(new Request.Options(1000, 4 * 1000))).isEqualTo("foo");
  }

  @Test
  void normalResponseForChildOptionsTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api = Feign.builder().options(new ChildOptions(1000, 1000))
        .target(OptionsInterface.class, server.url("/").toString());

    assertThat(api.getChildOptions(new ChildOptions(1000, 4 * 1000))).isEqualTo("foo");
  }

  @Test
  void socketTimeoutWithMethodOptionsTest() throws Exception {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(2, TimeUnit.SECONDS));
    Request.Options options = new Request.Options(1000, 3000);
    final OptionsInterface api = Feign.builder().options(options).target(OptionsInterface.class,
        server.url("/").toString());

    AtomicReference<Exception> exceptionAtomicReference = new AtomicReference<>();
    Thread thread = new Thread(() -> {
      try {
        options.setMethodOptions("get", new Request.Options(1000, 1000));
        api.get();
      } catch (Exception exception) {
        exceptionAtomicReference.set(exception);
      }
    });
    thread.start();
    thread.join();

    Exception exception = exceptionAtomicReference.get();
    assertThat(exception).isInstanceOf(FeignException.class);
    assertThat(exception).hasCauseInstanceOf(SocketTimeoutException.class);
  }

  @Test
  void normalResponseWithMethodOptionsTest() throws Exception {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(2, TimeUnit.SECONDS));
    Request.Options options = new Request.Options(1000, 1000);
    final OptionsInterface api = Feign.builder().options(options).target(OptionsInterface.class,
        server.url("/").toString());

    CountDownLatch countDownLatch = new CountDownLatch(1);
    Thread thread = new Thread(() -> {
      options.setMethodOptions("get", new Request.Options(1000, 3000));
      api.get();
      countDownLatch.countDown();
    });
    thread.start();
    thread.join();
  }
}
