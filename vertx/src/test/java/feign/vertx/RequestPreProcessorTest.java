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
import static feign.vertx.testcase.domain.Flavor.FLAVORS_JSON;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Test request pre processor")
class RequestPreProcessorTest extends AbstractFeignVertxTest {
  IcecreamServiceApi client;

  @BeforeEach
  void createClient(Vertx vertx) {
    client =
        VertxFeign.builder()
            .vertx(vertx)
            .options(new HttpClientOptions().setLogActivity(true))
            .decoder(new JacksonDecoder(TestUtils.MAPPER))
            .requestPreProcessor(req -> req.putHeader("version", "v1"))
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(IcecreamServiceApi.class, wireMock.baseUrl());
  }

  @Test
  @DisplayName("request pre processor must be applied")
  void requestPreProcessorMustApply(VertxTestContext testContext) {

    /* Given */
    wireMock.stubFor(
        get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("version", equalTo("v1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(100)
                    .withHeader("Content-Type", "application/json")
                    .withBody(FLAVORS_JSON)));

    Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(
        res ->
            testContext.verify(
                () -> {
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
