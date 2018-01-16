package feign.vertx;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import feign.Logger;
import feign.Request;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.IcecreamServiceApi;
import feign.vertx.testcase.domain.*;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the new capability of Vertx Feign client to support both Feign
 * Request.Options (regression) and the new HttpClientOptions configuration.
 */
@RunWith(VertxUnitRunner.class)
public class VertxHttpOptionsTest {
  private Vertx vertx = Vertx.vertx();

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(8089);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private String flavorsStr;

  @Before
  public void setUp() throws Exception {
    /* Given */
  flavorsStr = Arrays
            .stream(Flavor.values())
            .map(flavor -> "\"" + flavor + "\"")
            .collect(Collectors.joining(", ", "[ ", " ]"));
  }


  /**
   * Test the Vert.x HttpClientOptions version of the Vert.x Feign Client.
   * This is useful for use-cases like HTTPS/2 or gzip compressed responses.
   * @param context Test Context
   */
  @Test
  public void testHttpClientOptions(TestContext context) {
    // NOTE: We cannot use HTTPS/2 because Wiremock 2.1.0-beta uses Jetty 9.2.13.v20150730.
    //       Jetty 9.3 is required for HTTPS/2. So we use HttpClientOptions.TryUseCompression(true)
    //       instead to verify we're using the Vert.x HttpClientOptions object.
    HttpClientOptions options = new HttpClientOptions().
            setTryUseCompression(true). // Attribute under test, not available with Feign Request.Options
            setConnectTimeout(5000).
            setIdleTimeout(5000);

    IcecreamServiceApi client = VertxFeign
            .builder()
            .vertx(vertx)
            .options(options) // New feature! Accepts HttpClientOptions
            .decoder(new JacksonDecoder(TestUtils.MAPPER))
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(IcecreamServiceApi.class, "http://localhost:8089");

    stubFor(get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Accept-Encoding", equalTo("deflate, gzip")) // Test setTryUseCompression(true) affected the request
            .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(flavorsStr)));

    testClient(client, context);
  }


  /**
   * Test the Feign Request Options version of the Vert.x Feign Client.
   * This proves regression is not broken for existing use-cases.
   * @param context Test Context
   */
  @Test
  public void testRequestOptions(TestContext context) {
    IcecreamServiceApi client = VertxFeign
            .builder()
            .vertx(vertx)
            .options(new Request.Options(5000,5000) ) // Plain old Feign Request.Options (regression)
            .decoder(new JacksonDecoder(TestUtils.MAPPER))
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(IcecreamServiceApi.class, "http://localhost:8089");

    stubFor(get(urlEqualTo("/icecream/flavors"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Accept-Encoding", absent()) // Test that Accept-Encoding is missing (since we're using Request.Options)
            .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(flavorsStr)));

    testClient(client, context);
  }


  /**
   * Test the provided client for the correct results
   * @param client Feign client instance
   * @param context Test Context
   */
  private void testClient(IcecreamServiceApi client, TestContext context) {
    Async async = context.async();

    client.getAvailableFlavors().setHandler(res -> {
      if (res.succeeded()) {
        Collection<Flavor> flavors = res.result();

        try {
          assertThat(flavors)
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
}
