package feign.vertx;

import feign.Logger;
import feign.RetryableException;
import feign.Retryer;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.IcecreamServiceApi;
import feign.vertx.testcase.domain.Flavor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static feign.vertx.TestUtils.MAPPER;
import static feign.vertx.testcase.domain.Flavor.FLAVORS_JSON;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("When server ask client to retry")
public class RetryingTest extends AbstractFeignVertxTest {
  static IcecreamServiceApi client;

  @BeforeAll
  static void createClient(Vertx vertx) {
    client = VertxFeign
        .builder()
        .vertx(vertx)
        .decoder(new JacksonDecoder(MAPPER))
        .retryer(new Retryer.Default(100, SECONDS.toMillis(1), 5))
        .logger(new Slf4jLogger())
        .logLevel(Logger.Level.FULL)
        .target(IcecreamServiceApi.class, wireMock.baseUrl());
  }

  @Test
  @DisplayName("should succeed when client retries less than max attempts")
  public void testRetrying_success(VertxTestContext testContext) {

    /* Given */
    String scenario = "testRetrying_success";

    wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .inScenario(scenario)
        .whenScenarioStateIs(STARTED)
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Retry-After", "1"))
        .willSetStateTo("attempt1"));

    wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .inScenario(scenario)
        .whenScenarioStateIs("attempt1")
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Retry-After", "1"))
        .willSetStateTo("attempt2"));

    wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .inScenario(scenario)
        .whenScenarioStateIs("attempt2")
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(FLAVORS_JSON)));

    /* When */
    Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(res -> testContext.verify(() -> {
      if (res.succeeded()) {
        assertThat(res.result())
            .hasSize(Flavor.values().length)
            .containsAll(Arrays.asList(Flavor.values()));
        testContext.completeNow();
      } else {
        testContext.failNow(res.cause());
      }
    }));
  }

  @Test
  @DisplayName("should fail when after max number of attempts")
  public void testRetrying_noMoreAttempts(VertxTestContext testContext) {

    /* Given */
    wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Retry-After", "1")));

    /* When */
    Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(res -> testContext.verify(() -> {
      if (res.failed()) {
        assertThat(res.cause())
            .isInstanceOf(RetryableException.class)
            .hasMessageContaining("503 Service Unavailable");
        testContext.completeNow();
      } else {
        testContext.failNow(new IllegalStateException("RetryableException excepted but not occurred"));
      }
    }));
  }
}
