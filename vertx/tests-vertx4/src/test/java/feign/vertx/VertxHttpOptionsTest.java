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
import static feign.vertx.tests.testcase.domain.Flavor.FLAVORS_JSON;
import static org.assertj.core.api.Assertions.assertThat;

import feign.Logger;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.tests.TestUtils;
import feign.vertx.tests.testcase.IcecreamServiceApi;
import feign.vertx.tests.testcase.domain.Flavor;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxTestContext;
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FeignVertx client should be created from")
class VertxHttpOptionsTest extends AbstractFeignVertxTest {

  @BeforeAll
  static void setupStub() {
    wireMock.stubFor(
        get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(FLAVORS_JSON)));
  }

  @Test
  @DisplayName("HttpClientOptions from Vertx")
  void httpClientOptions(Vertx vertx, VertxTestContext testContext) {
    WebClientOptions options =
        new WebClientOptions()
            .setProtocolVersion(HttpVersion.HTTP_2)
            .setHttp2MaxPoolSize(1)
            .setConnectTimeout(5000)
            .setIdleTimeout(5000);
    WebClient webClient = WebClient.create(vertx, options);

    IcecreamServiceApi client =
        VertxFeign.builder()
            .webClient(webClient)
            .decoder(new JacksonDecoder(TestUtils.MAPPER))
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(IcecreamServiceApi.class, wireMock.baseUrl());

    testClient(client, testContext);
  }

  @Test
  @DisplayName("Request Options from Feign")
  void requestOptions(Vertx vertx, VertxTestContext testContext) {
    WebClientOptions options =
        new WebClientOptions()
            .setConnectTimeout(5000)
            .setIdleTimeout(5000)
            .setFollowRedirects(true);
    WebClient webClient = WebClient.create(vertx, options);

    IcecreamServiceApi client =
        VertxFeign.builder()
            .webClient(webClient)
            .decoder(new JacksonDecoder(TestUtils.MAPPER))
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(IcecreamServiceApi.class, wireMock.baseUrl());

    testClient(client, testContext);
  }

  private void testClient(IcecreamServiceApi client, VertxTestContext testContext) {
    client
        .getAvailableFlavors()
        .onComplete(
            res -> {
              if (res.succeeded()) {
                Collection<Flavor> flavors = res.result();

                assertThat(flavors)
                    .hasSize(Flavor.values().length)
                    .containsAll(Arrays.asList(Flavor.values()));
                testContext.completeNow();
              } else {
                testContext.failNow(res.cause());
              }
            });
  }
}
