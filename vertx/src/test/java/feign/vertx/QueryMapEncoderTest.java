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
import static org.assertj.core.api.Assertions.assertThat;

import feign.*;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.domain.Bill;
import feign.vertx.testcase.domain.Flavor;
import feign.vertx.testcase.domain.IceCreamOrder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tests of QueryMapEncoder")
class QueryMapEncoderTest extends AbstractFeignVertxTest {
  interface Api {

    @RequestLine("POST /icecream/orders")
    Future<Bill> makeOrder(@QueryMap IceCreamOrder order);
  }

  Api client;

  @BeforeEach
  void createClient(Vertx vertx) {
    client =
        VertxFeign.builder()
            .vertx(vertx)
            .decoder(new JacksonDecoder(TestUtils.MAPPER))
            .options(new HttpClientOptions().setLogActivity(true))
            .queryMapEncoder(new CustomQueryMapEncoder())
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(Api.class, wireMock.baseUrl());
  }

  @Test
  @DisplayName("QueryMapEncoder will be used")
  void willMakeOrder(VertxTestContext testContext) {

    /* Given */
    IceCreamOrder order = new IceCreamOrder();
    order.addBall(Flavor.PISTACHIO);
    order.addBall(Flavor.PISTACHIO);
    order.addBall(Flavor.STRAWBERRY);
    order.addBall(Flavor.BANANA);
    order.addBall(Flavor.VANILLA);

    Bill bill = Bill.makeBill(order);
    String billStr = TestUtils.encodeAsJsonString(bill);

    wireMock.stubFor(
        post(urlPathEqualTo("/icecream/orders"))
            .withQueryParam("balls", equalTo("BANANA:1,PISTACHIO:2,STRAWBERRY:1,VANILLA:1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(billStr)));

    /* When */
    Future<Bill> billFuture = client.makeOrder(order);

    /* Then */
    billFuture.onComplete(
        res ->
            testContext.verify(
                () -> {
                  if (res.succeeded()) {
                    assertThat(res.result()).isEqualTo(bill);
                    testContext.completeNow();
                  } else {
                    testContext.failNow(res.cause());
                  }
                }));
  }

  class CustomQueryMapEncoder implements QueryMapEncoder {
    @Override
    public Map<String, Object> encode(final Object o) {
      IceCreamOrder order = (IceCreamOrder) o;

      String balls =
          order.getBalls().entrySet().stream()
              .sorted(Comparator.comparing(en -> en.getKey().toString()))
              .map(entry -> entry.getKey().toString() + ':' + entry.getValue())
              .collect(Collectors.joining(","));

      return Collections.singletonMap("balls", balls);
    }
  }
}
