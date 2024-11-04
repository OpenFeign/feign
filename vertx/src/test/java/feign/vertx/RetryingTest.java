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
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static feign.vertx.TestUtils.MAPPER;
import static feign.vertx.testcase.domain.Flavor.FLAVORS_JSON;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("When server ask client to retry")
class RetryingTest extends AbstractFeignVertxTest {
  static IcecreamServiceApi client;

  @BeforeAll
  static void createClient(Vertx vertx) {
    client =
        VertxFeign.builder()
            .vertx(vertx)
            .decoder(new JacksonDecoder(MAPPER))
            .retryer(new Retryer.Default(100, SECONDS.toMillis(1), 5))
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(IcecreamServiceApi.class, wireMock.baseUrl());
  }

  @Test
  @DisplayName("should succeed when client retries less than max attempts")
  void retryingSuccess(VertxTestContext testContext) {

    /* Given */
    String scenario = "testRetrying_success";

    wireMock.stubFor(
        get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .inScenario(scenario)
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "1"))
            .willSetStateTo("attempt1"));

    wireMock.stubFor(
        get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .inScenario(scenario)
            .whenScenarioStateIs("attempt1")
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "1"))
            .willSetStateTo("attempt2"));

    wireMock.stubFor(
        get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .inScenario(scenario)
            .whenScenarioStateIs("attempt2")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(FLAVORS_JSON)));

    /* When */
    Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(
        res ->
            testContext.verify(
                () -> {
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
  void retryingNoMoreAttempts(VertxTestContext testContext) {

    /* Given */
    wireMock.stubFor(
        get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "1")));

    /* When */
    Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(
        res ->
            testContext.verify(
                () -> {
                  if (res.failed()) {
                    assertThat(res.cause())
                        .isInstanceOf(RetryableException.class)
                        .hasMessageContaining("503 Service Unavailable");
                    testContext.completeNow();
                  } else {
                    testContext.failNow(
                        new IllegalStateException("RetryableException excepted but not occurred"));
                  }
                }));
  }
}
