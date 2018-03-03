package feign.vertx;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import feign.Response;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.AnotherServiceAPI;
import feign.vertx.testcase.domain.Bill;
import feign.vertx.testcase.domain.Flavor;
import feign.vertx.testcase.domain.OrderGenerator;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.stream.Collectors;

@RunWith(VertxUnitRunner.class)
public class OtherTest {
  private Vertx vertx = Vertx.vertx();
  private OrderGenerator generator = new OrderGenerator();

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(8089);

  @Test
  public void testGetAvailableFlavors_returnsRawResultType(
      TestContext context) {

    /* Given */
    String flavorsStr = Arrays
        .stream(Flavor.values())
        .map(flavor -> "\"" + flavor + "\"")
        .collect(Collectors.joining(", ", "[ ", " ]"));

    stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(flavorsStr)));

    AnotherServiceAPI client = VertxFeign
        .builder()
        .vertx(vertx)
        .decoder(new JacksonDecoder(TestUtils.MAPPER))
        .target(AnotherServiceAPI.class, "http://localhost:8089");

    Async async = context.async();

    /* When */
    client.getAvailableFlavors().setHandler(res -> {

      /* Then */
      if (res.succeeded()) {
        Response response = res.result();
        try {
          String content = new BufferedReader(new InputStreamReader(
              response.body().asInputStream()))
              .lines()
              .collect(Collectors.joining("\n"));

          context.assertTrue(response.status() == 200);
          context.assertEquals(content, flavorsStr);
          async.complete();
        } catch (IOException ioException) {
          context.fail(ioException);
        }
      } else {
        context.fail(res.cause());
      }
    });
  }

  @Test
  public void testPayBill_retursEmptyBodyResponse(TestContext context) {

    /* Given */
    Bill bill = Bill.makeBill(generator.generate());
    String billStr = TestUtils.encodeAsJsonString(bill);

    stubFor(post(urlEqualTo("/icecream/bills/pay"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalToJson(billStr))
        .willReturn(aResponse()
            .withStatus(200)));

    AnotherServiceAPI client = VertxFeign
        .builder()
        .vertx(vertx)
        .encoder(new JacksonEncoder(TestUtils.MAPPER))
        .target(AnotherServiceAPI.class, "http://localhost:8089");

    Async async = context.async();

    /* When */
    client.payBill(bill).setHandler(res -> {

      /* Then */
      if (res.succeeded()) {
        async.complete();
      } else {
        context.fail(res.cause());
      }
    });
  }
}
