package feign.vertx;

import feign.Response;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.RawServiceAPI;
import feign.vertx.testcase.domain.Bill;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static feign.vertx.testcase.domain.Flavor.FLAVORS_JSON;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("When creating client from 'raw' contract")
public class RawContractTest extends AbstractFeignVertxTest {
  static RawServiceAPI client;

  @BeforeAll
  static void createClient(Vertx vertx) {
    client = VertxFeign
        .builder()
        .vertx(vertx)
        .encoder(new JacksonEncoder(TestUtils.MAPPER))
        .decoder(new JacksonDecoder(TestUtils.MAPPER))
        .target(RawServiceAPI.class, wireMock.baseUrl());
  }

  @Test
  @DisplayName("should get available flavors")
  public void testGetAvailableFlavors(VertxTestContext testContext) {

    /* Given */
    wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(FLAVORS_JSON)));

    /* When */
    Future<Response> flavorsFuture = client.getAvailableFlavors();

    /* Then */
    flavorsFuture.onComplete(res -> testContext.verify(() -> {
      if (res.succeeded()) {
        Response response = res.result();
        try {
          String content = new BufferedReader(new InputStreamReader(
              response.body().asInputStream()))
              .lines()
              .collect(Collectors.joining("\n"));

          assertThat(response.status())
              .isEqualTo(200);
          assertThat(content)
              .isEqualTo(FLAVORS_JSON);
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
  public void testPayBill(VertxTestContext testContext) {

    /* Given */
    Bill bill = Bill.makeBill(generator.generate());
    String billStr = TestUtils.encodeAsJsonString(bill);

    wireMock.stubFor(post(urlEqualTo("/icecream/bills/pay"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalToJson(billStr))
        .willReturn(aResponse()
            .withStatus(200)));

    /* When */
    Future<Response> payedFuture = client.payBill(bill);

    /* Then */
    payedFuture.onComplete(res -> testContext.verify(() -> {
      if (res.succeeded()) {
        testContext.completeNow();
      } else {
        testContext.failNow(res.cause());
      }
    }));
  }
}
