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
package feign.vertx;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractClientReconnectTest extends AbstractFeignVertxTest {
  static String baseUrl;
  static int serverPort;

  HelloServiceAPI client = null;

  @BeforeAll
  static void setupMockServer() {
    serverPort = wireMock.port();
    baseUrl = wireMock.baseUrl();
    wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)));
  }

  @BeforeAll
  protected abstract void createClient(Vertx vertx);

  @Test
  @DisplayName("All requests should be answered")
  void allRequestsShouldBeAnswered(VertxTestContext testContext) {
    sendRequests(10).compose(responses -> assertAllRequestsAnswered(responses, testContext));
  }

  @Nested
  @DisplayName("After server has became unavailable")
  class AfterServerBecameUnavailable {

    @BeforeEach
    void shutDownServer() {
      wireMock.stop();
    }

    @Test
    @DisplayName("All requests should fail")
    void allRequestsShouldFail(VertxTestContext testContext) {
      sendRequests(10)
          .onComplete(
              responses ->
                  testContext.verify(
                      () -> {
                        if (responses.succeeded()) {
                          testContext.failNow(
                              new IllegalStateException(
                                  "Client should not get responses from unavailable server"));
                        }

                        try {
                          assertThat(responses.cause().getMessage()).startsWith("Connection ");
                          testContext.completeNow();
                        } catch (Throwable assertionException) {
                          testContext.failNow(assertionException);
                        }
                      }));
    }

    @Nested
    @DisplayName("After server is available again")
    class AfterServerIsBack {
      WireMockServer restartedServer = new WireMockServer(options().port(serverPort));

      @BeforeEach
      void restartServer() {
        restartedServer.start();
        restartedServer.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)));
      }

      @AfterEach
      void shutDownServer() {
        restartedServer.stop();
      }

      @Test
      @DisplayName("All requests should be answered")
      void allRequestsShouldBeAnswered(VertxTestContext testContext) {
        sendRequests(10).compose(responses -> assertAllRequestsAnswered(responses, testContext));
      }
    }
  }

  CompositeFuture sendRequests(int requests) {
    List<Future> requestList =
        IntStream.range(0, requests)
            .mapToObj(ignored -> client.hello())
            .collect(Collectors.toList());
    return CompositeFuture.all(requestList);
  }

  Future<Void> assertAllRequestsAnswered(
      AsyncResult<CompositeFuture> responses, VertxTestContext testContext) {
    if (responses.succeeded()) {
      testContext.completeNow();
      return Future.succeededFuture();
    } else {
      testContext.failNow(responses.cause());
      return Future.failedFuture(responses.cause());
    }
  }
}
