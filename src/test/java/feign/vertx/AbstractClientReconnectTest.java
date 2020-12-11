package feign.vertx;

import com.github.tomakehurst.wiremock.WireMockServer;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractClientReconnectTest extends AbstractFeignVertxTest {
  static String baseUrl;
  static int serverPort;

  HelloServiceAPI client = null;

  @BeforeAll
  static void setupMockServer() {
    serverPort = wireMock.port();
    baseUrl = wireMock.baseUrl();
    wireMock.stubFor(get(anyUrl())
            .willReturn(aResponse()
                    .withStatus(200)));
  }

  @BeforeAll
  protected abstract void createClient(Vertx vertx);

  @Test
  @DisplayName("All requests should be answered")
  void testAllRequestsShouldBeAnswered(VertxTestContext testContext) {
    sendRequests(10)
        .compose(responses -> assertAllRequestsAnswered(responses, testContext));
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
    void testAllRequestsShouldFail(VertxTestContext testContext) {
      sendRequests(10)
          .onComplete(responses -> testContext.verify(() -> {
            if (responses.succeeded()) {
              testContext.failNow(new IllegalStateException("Client should not get responses from unavailable server"));
            }

            try {
              assertThat(responses.cause().getMessage())
                  .startsWith("Connection ");
              testContext.completeNow();
            } catch(Throwable assertionException) {
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
        restartedServer.stubFor(get(anyUrl())
            .willReturn(aResponse()
                .withStatus(200)));
      }

      @AfterEach
      void shutDownServer() {
        restartedServer.stop();
      }

      @Test
      @DisplayName("All requests should be answered")
      void testAllRequestsShouldBeAnswered(VertxTestContext testContext) {
        sendRequests(10)
            .compose(responses -> assertAllRequestsAnswered(responses, testContext));
      }
    }
  }

  CompositeFuture sendRequests(int requests) {
    List<Future> requestList = IntStream
        .range(0, requests)
        .mapToObj(ignored -> client.hello())
        .collect(Collectors.toList());
    return CompositeFuture.all(requestList);
  }

  Future<Void> assertAllRequestsAnswered(AsyncResult<CompositeFuture> responses, VertxTestContext testContext) {
    if (responses.succeeded()) {
      testContext.completeNow();
      return Future.succeededFuture();
    } else {
      testContext.failNow(responses.cause());
      return Future.failedFuture(responses.cause());
    }
  }
}
