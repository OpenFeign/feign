package feign.vertx;

import feign.Logger;
import feign.QueryMap;
import feign.QueryMapEncoder;
import feign.RequestLine;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.domain.Bill;
import feign.vertx.testcase.domain.Flavor;
import feign.vertx.testcase.domain.IceCreamOrder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tests of QueryMapEncoder")
public class QueryMapEncoderTest extends AbstractFeignVertxTest {
  interface Api {

    @RequestLine("POST /icecream/orders")
    Future<Bill> makeOrder(@QueryMap IceCreamOrder order);
  }

  Api client;

  @BeforeEach
  void createClient(Vertx vertx) {
    client = VertxFeign
        .builder()
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
  void testWillMakeOrder(VertxTestContext testContext) {

    /* Given */
    IceCreamOrder order = new IceCreamOrder();
    order.addBall(Flavor.PISTACHIO);
    order.addBall(Flavor.PISTACHIO);
    order.addBall(Flavor.STRAWBERRY);
    order.addBall(Flavor.BANANA);
    order.addBall(Flavor.VANILLA);

    Bill bill = Bill.makeBill(order);
    String billStr = TestUtils.encodeAsJsonString(bill);

    wireMock.stubFor(post(urlEqualTo("/icecream/orders?balls=BANANA:1,PISTACHIO:2,STRAWBERRY:1,VANILLA:1"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(billStr)));

    /* When */
    Future<Bill> billFuture = client.makeOrder(order);

    /* Then */
    billFuture.onComplete(res -> testContext.verify(() -> {
      if (res.succeeded()) {
        assertThat(res.result())
            .isEqualTo(bill);
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

      String balls = order
          .getBalls()
          .entrySet()
          .stream()
          .sorted(Comparator.comparing(en -> en.getKey().toString()))
          .map(entry -> entry.getKey().toString() + ':' + entry.getValue())
          .collect(Collectors.joining(","));

      Map<String, Object> encoded = new HashMap<>();
      encoded.put("balls", balls);
      return encoded;
    }
  }
}
