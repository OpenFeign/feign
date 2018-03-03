package feign.vertx;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import feign.FeignException;
import feign.Logger;
import feign.Request;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.IcecreamServiceApi;
import feign.vertx.testcase.IcecreamServiceApiBroken;
import feign.vertx.testcase.domain.Bill;
import feign.vertx.testcase.domain.Flavor;
import feign.vertx.testcase.domain.IceCreamOrder;
import feign.vertx.testcase.domain.Mixin;
import feign.vertx.testcase.domain.OrderGenerator;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@RunWith(VertxUnitRunner.class)
public class VertxHttpClientTest {
  private Vertx vertx = Vertx.vertx();
  private OrderGenerator generator = new OrderGenerator();

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(8089);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void testSimpleGet_success(TestContext context) {

    /* Given */
    String flavorsStr = Arrays
        .stream(Flavor.values())
        .map(flavor -> "\"" + flavor + "\"")
        .collect(Collectors.joining(", ", "[ ", " ]"));

    String mixinsStr = Arrays
        .stream(Mixin.values())
        .map(flavor -> "\"" + flavor + "\"")
        .collect(Collectors.joining(", ", "[ ", " ]"));

    stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(flavorsStr)));

    stubFor(get(urlEqualTo("/icecream/mixins"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(mixinsStr)));

    IcecreamServiceApi client = VertxFeign
        .builder()
        .vertx(vertx)
        .decoder(new JacksonDecoder(TestUtils.MAPPER))
        .logger(new Slf4jLogger())
        .logLevel(Logger.Level.FULL)
        .target(IcecreamServiceApi.class, "http://localhost:8089");

    Async async = context.async();

    /* When */
    Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();
    Future<Collection<Mixin>> mixinsFuture = client.getAvailableMixins();

    /* Then */
    CompositeFuture.all(flavorsFuture, mixinsFuture).setHandler(res -> {
      if (res.succeeded()) {
        Collection<Flavor> flavors = res.result().resultAt(0);
        Collection<Mixin> mixins = res.result().resultAt(1);

        try {
          assertThat(flavors)
              .hasSize(Flavor.values().length)
              .containsAll(Arrays.asList(Flavor.values()));
          assertThat(mixins)
              .hasSize(Mixin.values().length)
              .containsAll(Arrays.asList(Mixin.values()));
          async.complete();
        } catch (Throwable exception) {
          context.fail(exception);
        }
      } else {
        context.fail(res.cause());
      }
    });
  }

  @Test
  public void testFindOrder_success(TestContext context) {

    /* Given */
    IceCreamOrder order = generator.generate();
    int orderId = order.getId();
    String orderStr = TestUtils.encodeAsJsonString(order);

    stubFor(get(urlEqualTo("/icecream/orders/" + orderId))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(orderStr)));

    IcecreamServiceApi client = VertxFeign
        .builder()
        .vertx(vertx)
        .decoder(new JacksonDecoder(TestUtils.MAPPER))
        .options(new Request.Options(5 * 1000, 10 * 1000))
        .target(IcecreamServiceApi.class, "http://localhost:8089");

    Async async = context.async();

    /* When */
    client.findOrder(orderId).setHandler(res -> {

      /* Then */
      if (res.succeeded()) {
        try {
          Assertions.assertThat(res.result())
              .isEqualToComparingFieldByFieldRecursively(order);
          async.complete();
        } catch (Throwable exception) {
          context.fail(exception);
        }
      } else {
        context.fail(res.cause());
      }
    });
  }

  @Test
  public void testFindOrder_404(TestContext context) {

    /* Given */
    stubFor(get(urlEqualTo("/icecream/orders/123"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(404)));

    IcecreamServiceApi client = VertxFeign
        .builder()
        .vertx(vertx)
        .decoder(new JacksonDecoder(TestUtils.MAPPER))
        .target(IcecreamServiceApi.class, "http://localhost:8089");

    Async async = context.async();

    /* When */
    client.findOrder(123).setHandler(res -> {

      /* Then */
      if (res.failed()) {
        try {
          assertThat(res.cause())
              .isInstanceOf(FeignException.class)
              .hasMessageContaining("status 404");
          async.complete();
        } catch (Throwable exception) {
          context.fail(exception);
        }
      } else {
        context.fail("FeignException excepted but not occurred");
      }
    });
  }

  @Test
  public void testMakeOrder_success(TestContext context) {

    /* Given */
    IceCreamOrder order = generator.generate();
    Bill bill = Bill.makeBill(order);
    String orderStr = TestUtils.encodeAsJsonString(order);
    String billStr = TestUtils.encodeAsJsonString(bill);

    stubFor(post(urlEqualTo("/icecream/orders"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("Accept", equalTo("application/json"))
        .withRequestBody(equalToJson(orderStr))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(billStr)));

    IcecreamServiceApi client = VertxFeign
        .builder()
        .vertx(vertx)
        .encoder(new JacksonEncoder(TestUtils.MAPPER))
        .decoder(new JacksonDecoder(TestUtils.MAPPER))
        .target(IcecreamServiceApi.class, "http://localhost:8089");

    Async async = context.async();

    /* When */
    client.makeOrder(order).setHandler(res -> {

      /* Then */
      if (res.succeeded()) {
        try {
          Assertions.assertThat(res.result())
              .isEqualToComparingFieldByFieldRecursively(bill);
          async.complete();
        } catch (Throwable exception) {
          context.fail(exception);
        }
      } else {
        context.fail(res.cause());
      }
    });
  }

  @Test
  public void testPayBill_success(TestContext context) {

    /* Given */
    Bill bill = Bill.makeBill(generator.generate());
    String billStr = TestUtils.encodeAsJsonString(bill);

    stubFor(post(urlEqualTo("/icecream/bills/pay"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalToJson(billStr))
        .willReturn(aResponse()
            .withStatus(200)));

    IcecreamServiceApi client = VertxFeign
        .builder()
        .vertx(vertx)
        .encoder(new JacksonEncoder(TestUtils.MAPPER))
        .target(IcecreamServiceApi.class, "http://localhost:8089");

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

  @Test
  public void testInstantiationContract_forgotProvideVertx() {

    /* Given */
    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage(
        "Vertx instance wasn't provided in VertxFeign builder");

    /* When */
    VertxFeign
        .builder()
        .target(IcecreamServiceApi.class, "http://localhost:8089");

    /* Then */
    // Exception thrown
  }

  @Test
  public void testInstantiationBrokenContract_throwsException() {

    /* Given */
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage(containsString
        ("IcecreamServiceApiBroken#findOrder(int)"));

    /* When */
    VertxFeign
        .builder()
        .vertx(vertx)
        .target(IcecreamServiceApiBroken.class, "http://localhost:8089");

    /* Then */
    // Exception thrown
  }
}
