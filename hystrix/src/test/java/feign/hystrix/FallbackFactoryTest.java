/*
 * Copyright 2012-2024 The Feign Authors
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
package feign.hystrix;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import feign.FeignException;
import feign.RequestLine;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class FallbackFactoryTest {

  interface TestInterface {
    @RequestLine("POST /")
    String invoke();
  }

  public final MockWebServer server = new MockWebServer();

  @Test
  void fallbackFactory_example_lambda() {
    server.enqueue(new MockResponse().setResponseCode(500));
    server.enqueue(new MockResponse().setResponseCode(404));

    TestInterface api = target(cause -> () -> {
      assertThat(cause).isInstanceOf(FeignException.class);
      return ((FeignException) cause).status() == 500 ? "foo" : "bar";
    });

    assertThat(api.invoke()).isEqualTo("foo");
    assertThat(api.invoke()).isEqualTo("bar");
  }

  static class FallbackApiWithCtor implements TestInterface {
    final Throwable cause;

    FallbackApiWithCtor(Throwable cause) {
      this.cause = cause;
    }

    @Override
    public String invoke() {
      return "foo";
    }
  }

  @Test
  void fallbackFactory_example_ctor() {
    server.enqueue(new MockResponse().setResponseCode(500));

    // method reference
    TestInterface api = target(FallbackApiWithCtor::new);

    assertThat(api.invoke()).isEqualTo("foo");

    server.enqueue(new MockResponse().setResponseCode(500));

    // lambda factory
    api = target(FallbackApiWithCtor::new);

    server.enqueue(new MockResponse().setResponseCode(500));

    // old school
    api = target(FallbackApiWithCtor::new);

    assertThat(api.invoke()).isEqualTo("foo");
  }

  // retrofit so people don't have to track 2 classes
  static class FallbackApiRetro implements TestInterface, FallbackFactory<FallbackApiRetro> {

    @Override
    public FallbackApiRetro create(Throwable cause) {
      return new FallbackApiRetro(cause);
    }

    final Throwable cause; // nullable

    public FallbackApiRetro() {
      this(null);
    }

    FallbackApiRetro(Throwable cause) {
      this.cause = cause;
    }

    @Override
    public String invoke() {
      return cause != null ? cause.getMessage() : "foo";
    }
  }

  @Test
  void fallbackFactory_example_retro() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target(new FallbackApiRetro());

    assertThat(api.invoke()).isEqualTo(
        "[500 Server Error] during [POST] to [http://localhost:" + server.getPort()
            + "/] [TestInterface#invoke()]: []");
  }

  @Test
  void defaultFallbackFactory_delegates() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target(new FallbackFactory.Default<>(() -> "foo"));

    assertThat(api.invoke())
        .isEqualTo("foo");
  }

  @Test
  void defaultFallbackFactory_doesntLogByDefault() {
    server.enqueue(new MockResponse().setResponseCode(500));

    Logger logger = new Logger("", null) {
      @Override
      public void log(Level level, String msg, Throwable thrown) {
        throw new AssertionError("logged eventhough not FINE level");
      }
    };

    target(new FallbackFactory.Default<>(() -> "foo", logger)).invoke();
  }

  @Test
  void defaultFallbackFactory_logsAtFineLevel() {
    server.enqueue(new MockResponse().setResponseCode(500));

    AtomicBoolean logged = new AtomicBoolean();
    Logger logger = new Logger("", null) {
      @Override
      public void log(Level level, String msg, Throwable thrown) {
        logged.set(true);

        assertThat(msg)
            .isEqualTo("fallback due to: [500 Server Error] during [POST] to [http://localhost:"
                + server.getPort() + "/] [TestInterface#invoke()]: []");
        assertThat(thrown).isInstanceOf(FeignException.class);
      }
    };
    logger.setLevel(Level.FINE);

    target(new FallbackFactory.Default<>(() -> "foo", logger)).invoke();
    assertThat(logged.get()).isTrue();
  }

  TestInterface target(FallbackFactory<? extends TestInterface> factory) {
    return HystrixFeign.builder()
        .target(TestInterface.class, "http://localhost:" + server.getPort(), factory);
  }

  @AfterEach
  void afterEachTest() throws IOException {
    server.close();
  }
}
