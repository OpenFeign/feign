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

import feign.Client;
import feign.Feign;
import feign.RequestLine;
import feign.Response;
import feign.Retryer;
import feign.Target.EmptyTarget;
import feign.Target.HardCodedTarget;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.net.URI;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TargetHostAndPortFeignObservationConvention}, verifying:
 *
 * <ul>
 *   <li>the {@link DefaultFeignObservationConvention} (the default) never emits host/port;
 *   <li>the opt-in convention emits host and port when the target url has an explicit port;
 *   <li>the opt-in convention emits host but no port when the target url has no explicit port;
 *   <li>an {@link EmptyTarget} (whose {@code url()} throws) never breaks the observation and simply
 *       omits host/port.
 * </ul>
 *
 * <p>No real network is used: the underlying {@link Client} is stubbed to return a canned 200
 * response.
 */
class TargetHostAndPortFeignObservationConventionTest {

  private static final String TARGET_HOST =
      FeignObservationDocumentation.HttpClientTags.TARGET_HOST.asString();
  private static final String TARGET_PORT =
      FeignObservationDocumentation.HttpClientTags.TARGET_PORT.asString();

  interface TestClient {
    @RequestLine("GET /")
    String get();
  }

  interface AbsoluteUrlClient {
    @RequestLine("GET")
    String get(URI uri);
  }

  private TestObservationRegistry observationRegistry;

  @BeforeEach
  void setUp() {
    this.observationRegistry = TestObservationRegistry.create();
  }

  private static Client okClient() {
    return (request, options) ->
        Response.builder()
            .status(200)
            .reason("OK")
            .request(request)
            .headers(Collections.emptyMap())
            .build();
  }

  @Test
  void defaultConventionDoesNotEmitHostOrPort() {
    TestClient feignClient =
        Feign.builder()
            .client(okClient())
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost:8080"));

    feignClient.get();

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .doesNotHaveLowCardinalityKeyValueWithKey(TARGET_HOST)
        .doesNotHaveLowCardinalityKeyValueWithKey(TARGET_PORT);
  }

  @Test
  void optInConventionEmitsHostAndPortWhenPortIsPresent() {
    TestClient feignClient =
        Feign.builder()
            .client(okClient())
            .addCapability(
                new MicrometerObservationCapability(
                    observationRegistry, TargetHostAndPortFeignObservationConvention.INSTANCE))
            .target(new HardCodedTarget<>(TestClient.class, "http://example.com:8080"));

    feignClient.get();

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasLowCardinalityKeyValue(TARGET_HOST, "example.com")
        .hasLowCardinalityKeyValue(TARGET_PORT, "8080");
  }

  @Test
  void optInConventionEmitsHostButNoPortWhenPortIsAbsent() {
    TestClient feignClient =
        Feign.builder()
            .client(okClient())
            .addCapability(
                new MicrometerObservationCapability(
                    observationRegistry, TargetHostAndPortFeignObservationConvention.INSTANCE))
            .target(new HardCodedTarget<>(TestClient.class, "http://example.com"));

    feignClient.get();

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasLowCardinalityKeyValue(TARGET_HOST, "example.com")
        .doesNotHaveLowCardinalityKeyValueWithKey(TARGET_PORT);
  }

  @Test
  void optInConventionIsSafeWithEmptyTarget() {
    AbsoluteUrlClient feignClient =
        Feign.builder()
            .client(okClient())
            .retryer(Retryer.NEVER_RETRY)
            .addCapability(
                new MicrometerObservationCapability(
                    observationRegistry, TargetHostAndPortFeignObservationConvention.INSTANCE))
            .target(EmptyTarget.create(AbsoluteUrlClient.class));

    // EmptyTarget.url() throws UnsupportedOperationException; the convention must swallow it and
    // simply omit host/port rather than break the observation.
    feignClient.get(URI.create("http://absolute.example.com:9000/resource"));

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .doesNotHaveLowCardinalityKeyValueWithKey(TARGET_HOST)
        .doesNotHaveLowCardinalityKeyValueWithKey(TARGET_PORT);
  }
}
