package feign.vertx;

import feign.Logger;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.IcecreamServiceApi;
import feign.vertx.testcase.domain.Flavor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static feign.vertx.testcase.domain.Flavor.FLAVORS_JSON;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tests of handling of timeouts")
public class TimeoutHandlingTest extends AbstractFeignVertxTest {
  IcecreamServiceApi client;

  @BeforeEach
  void createClient(Vertx vertx) {
    client = VertxFeign
        .builder()
        .vertx(vertx)
        .decoder(new JacksonDecoder(TestUtils.MAPPER))
        .timeout(1000)
        .options(new HttpClientOptions().setLogActivity(true))
        .logger(new Slf4jLogger())
        .logLevel(Logger.Level.FULL)
        .target(IcecreamServiceApi.class, wireMock.baseUrl());
  }

  @Test
  @DisplayName("when timeout is reached")
  void testWhenTimeoutIsReached(VertxTestContext testContext) {

    /* Given */
    wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withFixedDelay(1500)
            .withHeader("Content-Type", "application/json")
            .withBody(FLAVORS_JSON)));

    Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(res -> testContext.verify(() -> {
      if (res.succeeded()) {
        testContext.failNow("should timeout!");
      } else {
        assertThat(res.cause())
            .isInstanceOf(TimeoutException.class)
            .hasMessageContaining("timeout");
        testContext.completeNow();
      }
    }));
  }

  @Test
  @DisplayName("when timeout is not reached")
  void testWhenTimeoutIsNotReached(VertxTestContext testContext) {

    /* Given */
    wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withFixedDelay(100)
            .withHeader("Content-Type", "application/json")
            .withBody(FLAVORS_JSON)));

    Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(res -> testContext.verify(() -> {
      if (res.succeeded()) {
        Collection<Flavor> flavors = res.result();
        assertThat(flavors)
            .hasSize(Flavor.values().length)
            .containsAll(Arrays.asList(Flavor.values()));
        testContext.completeNow();
      } else {
        testContext.failNow(res.cause());
      }
    }));
  }
}
