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
package feign.micrometer;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import feign.AsyncFeign;
import feign.Feign;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.RequestReplySenderContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
class FeignHeaderInstrumentationTest {

  static final String METER_NAME = "http.client.requests";

  MeterRegistry meterRegistry = new SimpleMeterRegistry();

  ObservationRegistry observationRegistry = ObservationRegistry.create();

  @BeforeEach
  void setup() {
    observationRegistry
        .observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    observationRegistry.observationConfig().observationHandler(new HeaderMutatingHandler());
  }

  @Test
  void getTemplatedPathForUri(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(get(anyUrl()).willReturn(ok()));

    TestClient testClient = clientInstrumentedWithObservations(wmRuntimeInfo.getHttpBaseUrl());
    testClient.templated("1", "2");

    verify(getRequestedFor(urlEqualTo("/customers/1/carts/2")).withHeader("foo", equalTo("bar")));
    Timer timer = meterRegistry.get(METER_NAME).timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();

    assertTags();
  }

  @Test
  void getTemplatedPathForUriForAsync(WireMockRuntimeInfo wmRuntimeInfo)
      throws ExecutionException, InterruptedException {
    stubFor(get(anyUrl()).willReturn(ok()));

    AsyncTestClient testClient =
        asyncClientInstrumentedWithObservations(wmRuntimeInfo.getHttpBaseUrl());
    testClient.templated("1", "2").get();

    verify(getRequestedFor(urlEqualTo("/customers/1/carts/2")).withHeader("foo", equalTo("bar")));
    Timer timer = meterRegistry.get(METER_NAME).timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();

    assertTags();
  }

  private void assertTags() {
    Id requestsId = meterRegistry.get(METER_NAME).meter().getId();

    assertMetricIdIncludesMethod(requestsId);
    assertMetricIdIncludesURI(requestsId);
    assertMetricIdIncludesStatus(requestsId);
    assertsMetricIdIncludesClientName(requestsId);
  }

  private void assertMetricIdIncludesMethod(Id metricId) {
    String tag = metricId.getTag("http.method");
    assertThat(tag)
        .as("Expect all metric names to have tag 'http.method': " + metricId)
        .isNotNull();
    assertThat(tag).as("Expect method to be GET: " + metricId).isEqualTo("GET");
  }

  private void assertMetricIdIncludesURI(Id metricId) {
    String tag = metricId.getTag("http.url");
    assertThat(tag).as("Expect all metric names to have tag 'http.url': " + metricId).isNotNull();
    assertThat(tag)
        .as("Expect url to match path template: " + metricId)
        .isEqualTo("/customers/{customerId}/carts/{cartId}");
  }

  private void assertMetricIdIncludesStatus(Id metricId) {
    String tag = metricId.getTag("http.status_code");
    assertThat(tag)
        .as("Expect all metric names to have tag 'http.status_code': " + metricId)
        .isNotNull();
    assertThat(tag).as("Expect status to be 200: " + metricId).isEqualTo("200");
  }

  private void assertsMetricIdIncludesClientName(Id metricId) {
    String tag = metricId.getTag("clientName");
    assertThat(tag).as("Expect all metric names to have tag 'clientName': " + metricId).isNotNull();
    assertThat(tag)
        .as("Expect class to be present: " + metricId)
        .startsWith("feign.micrometer.FeignHeaderInstrumentationTest$");
    assertThat(tag).endsWith("TestClient");
  }

  private TestClient clientInstrumentedWithObservations(String url) {
    return Feign.builder()
        .client(new feign.okhttp.OkHttpClient(new OkHttpClient()))
        .addCapability(new MicrometerObservationCapability(this.observationRegistry))
        .target(TestClient.class, url);
  }

  private AsyncTestClient asyncClientInstrumentedWithObservations(String url) {
    return AsyncFeign.builder()
        .client(new feign.okhttp.OkHttpClient(new OkHttpClient()))
        .addCapability(new MicrometerObservationCapability(this.observationRegistry))
        .target(AsyncTestClient.class, url);
  }

  public interface TestClient {

    @RequestLine("GET /customers/{customerId}/carts/{cartId}")
    String templated(@Param("customerId") String customerId, @Param("cartId") String cartId);
  }

  public interface AsyncTestClient {

    @RequestLine("GET /customers/{customerId}/carts/{cartId}")
    CompletableFuture<String> templated(
        @Param("customerId") String customerId, @Param("cartId") String cartId);
  }

  static class HeaderMutatingHandler
      implements ObservationHandler<RequestReplySenderContext<Request, Response>> {

    @Override
    public void onStart(RequestReplySenderContext<Request, Response> context) {
      Request carrier = context.getCarrier();
      carrier.header("foo", "bar");
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
      return context instanceof FeignContext;
    }
  }
}
