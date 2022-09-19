/*
 * Copyright 2012-2022 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.micrometer;

import java.util.concurrent.TimeUnit;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import feign.Feign;
import feign.Param;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.RequestReplySenderContext;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class FeignHeaderInstrumentationTest {

  MeterRegistry meterRegistry = new SimpleMeterRegistry();

  ObservationRegistry observationRegistry = ObservationRegistry.create();

  @BeforeEach
  void setup() {
    observationRegistry.observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    observationRegistry.observationConfig().observationHandler(new HeaderMutatingHandler());
  }

  @Test
  void getTemplatedPathForUri(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(get(anyUrl()).willReturn(ok()));

    TestClient testClient = clientInstrumentedWithObservations(wmRuntimeInfo.getHttpBaseUrl());
    testClient.templated("1", "2");

    verify(getRequestedFor(urlEqualTo("/customers/1/carts/2")).withHeader("foo", equalTo("bar")));
    Timer timer = meterRegistry.get("http.client.requests").timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
  }

  private TestClient clientInstrumentedWithObservations(String url) {
    return Feign.builder()
        .client(new feign.okhttp.OkHttpClient(new OkHttpClient()))
        .addCapability(new MicrometerCapability(meterRegistry, observationRegistry))
        .target(TestClient.class, url);
  }

  public interface TestClient {

    @RequestLine("GET /customers/{customerId}/carts/{cartId}")
    String templated(@Param("customerId") String customerId, @Param("cartId") String cartId);
  }

  static class HeaderMutatingHandler
      implements ObservationHandler<RequestReplySenderContext<RequestTemplate, Response>> {

    @Override
    public void onStart(RequestReplySenderContext<RequestTemplate, Response> context) {
      RequestTemplate carrier = context.getCarrier();
      carrier.header("foo", "bar");
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
      return context instanceof FeignContext;
    }
  }
}
