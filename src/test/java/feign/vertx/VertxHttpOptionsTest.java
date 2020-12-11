package feign.vertx;

import feign.Logger;
import feign.Request;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.IcecreamServiceApi;
import feign.vertx.testcase.domain.Flavor;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static feign.vertx.testcase.domain.Flavor.FLAVORS_JSON;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeignVertx client should be created from")
public class VertxHttpOptionsTest extends AbstractFeignVertxTest {

  @BeforeAll
  static void setupStub() {
    wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(FLAVORS_JSON)));
  }

  @Test
  @DisplayName("HttpClientOptions from Vertx")
  public void testHttpClientOptions(Vertx vertx, VertxTestContext testContext) {
    HttpClientOptions options = new HttpClientOptions()
        .setProtocolVersion(HttpVersion.HTTP_2)
        .setHttp2MaxPoolSize(1)
        .setConnectTimeout(5000)
        .setIdleTimeout(5000);

    IcecreamServiceApi client = VertxFeign
        .builder()
        .vertx(vertx)
        .options(options)
        .decoder(new JacksonDecoder(TestUtils.MAPPER))
        .logger(new Slf4jLogger())
        .logLevel(Logger.Level.FULL)
        .target(IcecreamServiceApi.class, wireMock.baseUrl());

    testClient(client, testContext);
  }

  @Test
  @DisplayName("Request Options from Feign")
  public void testRequestOptions(Vertx vertx, VertxTestContext testContext) {
    IcecreamServiceApi client = VertxFeign
            .builder()
            .vertx(vertx)
            .options(new Request.Options(5L, TimeUnit.SECONDS, 5L, TimeUnit.SECONDS, true))
            .decoder(new JacksonDecoder(TestUtils.MAPPER))
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(IcecreamServiceApi.class, wireMock.baseUrl());

    testClient(client, testContext);
  }

  private void testClient(IcecreamServiceApi client, VertxTestContext testContext) {
    client.getAvailableFlavors().onComplete(res -> {
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
