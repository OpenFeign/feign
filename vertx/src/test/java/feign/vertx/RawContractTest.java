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

import feign.Response;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.RawServiceAPI;
import feign.vertx.testcase.domain.Bill;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("When creating client from 'raw' contract")
class RawContractTest extends AbstractFeignVertxTest {
  static RawServiceAPI client;

  @BeforeAll
  static void createClient(Vertx vertx) {
    client =
        VertxFeign.builder()
            .vertx(vertx)
            .encoder(new JacksonEncoder(TestUtils.MAPPER))
            .decoder(new JacksonDecoder(TestUtils.MAPPER))
            .target(RawServiceAPI.class, wireMock.baseUrl());
  }

  @Test
  @DisplayName("should get available flavors")
  void getAvailableFlavors(VertxTestContext testContext) {

    /* Given */
    wireMock.stubFor(
        get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(FLAVORS_JSON)));

    /* When */
    Future<Response> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(
        res ->
            testContext.verify(
                () -> {
                  if (res.succeeded()) {
                    Response response = res.result();
                    try {
                      String content =
                          new BufferedReader(new InputStreamReader(response.body().asInputStream()))
                              .lines()
                              .collect(Collectors.joining("\n"));

                      assertThat(response.status()).isEqualTo(200);
                      assertThat(content).isEqualTo(FLAVORS_JSON);
                      testContext.completeNow();
                    } catch (IOException ioException) {
                      testContext.failNow(ioException);
                    }
                  } else {
                    testContext.failNow(res.cause());
                  }
                }));
  }

  @Test
  @DisplayName("should pay bill")
  void payBill(VertxTestContext testContext) {

    /* Given */
    Bill bill = Bill.makeBill(generator.generate());
    String billStr = TestUtils.encodeAsJsonString(bill);

    wireMock.stubFor(
        post(urlEqualTo("/icecream/bills/pay"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson(billStr))
            .willReturn(aResponse().withStatus(200)));

    /* When */
    Future<Response> payedFuture = client.payBill(bill);

    /* Then */
    payedFuture.onComplete(
        res ->
            testContext.verify(
                () -> {
                  if (res.succeeded()) {
                    testContext.completeNow();
                  } else {
                    testContext.failNow(res.cause());
                  }
                }));
  }
}
