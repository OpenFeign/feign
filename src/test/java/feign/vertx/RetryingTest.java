package feign.vertx;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static feign.vertx.TestUtils.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import feign.Logger;
import feign.RetryableException;
import feign.Retryer;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.IcecreamServiceApi;
import feign.vertx.testcase.domain.Flavor;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.stream.Collectors;

@RunWith(VertxUnitRunner.class)
public class RetryingTest {
  private Vertx vertx = Vertx.vertx();

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(8089);

  @Test
  public void testRetrying_success(TestContext context) {

    /* Given */
    String flavorsStr = Arrays
        .stream(Flavor.values())
        .map(flavor -> "\"" + flavor + "\"")
        .collect(Collectors.joining(", ", "[ ", " ]"));

    String scenario = "testRetrying_success";

    stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .inScenario(scenario)
        .whenScenarioStateIs(STARTED)
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Retry-After", "1"))
        .willSetStateTo("attempt1"));

    stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .inScenario(scenario)
        .whenScenarioStateIs("attempt1")
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Retry-After", "1"))
        .willSetStateTo("attempt2"));

    stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .inScenario(scenario)
        .whenScenarioStateIs("attempt2")
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(flavorsStr)));

    IcecreamServiceApi client = VertxFeign
        .builder()
        .vertx(vertx)
        .decoder(new JacksonDecoder(MAPPER))
        .retryer(new Retryer.Default())
        .target(IcecreamServiceApi.class, "http://localhost:8089");

    Async async = context.async();

    /* When */
    client.getAvailableFlavors().setHandler(res -> {

      /* Then */
      if (res.succeeded()) {
        try {
          assertThat(res.result())
              .hasSize(Flavor.values().length)
              .containsAll(Arrays.asList(Flavor.values()));
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
  public void testRetrying_noMoreAttempts(TestContext context) {

    /* Given */
    stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Retry-After", "1")));

    IcecreamServiceApi client = VertxFeign
        .builder()
        .vertx(vertx)
        .decoder(new JacksonDecoder(MAPPER))
        .retryer(new Retryer.Default())
        .logger(new Slf4jLogger())
        .logLevel(Logger.Level.FULL)
        .target(IcecreamServiceApi.class, "http://localhost:8089");

    Async async = context.async();

    /* When */
    client.getAvailableFlavors().setHandler(res -> {

      /* Then */
      if (res.failed())

      /* Then */
        if (res.failed()) {
          try {
            assertThat(res.cause())
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("503 Service Unavailable");
            async.complete();
          } catch (Throwable exception) {
            context.fail(exception);
          }
        } else {
          context.fail("RetryableException excepted but not occurred");
        }
    });
  }
}
