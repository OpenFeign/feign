/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.metrics5;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import feign.Feign;
import feign.RequestLine;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;

public class Metrics5CapabilityTest {

  public interface SimpleSource {

    @RequestLine("GET /get")
    String get(String body);

  }

  @Test
  public void addMetricsCapability() {
    final MetricRegistry registry = SharedMetricRegistries.getOrCreate("unit_test");

    final SimpleSource source = Feign.builder()
        .client(new MockClient()
            .ok(HttpMethod.GET, "/get", "1234567890abcde"))
        .addCapability(new Metrics5Capability(registry))
        .target(new MockTarget<>(Metrics5CapabilityTest.SimpleSource.class));

    source.get("0x3456789");

    assertThat(registry.getMetrics(), aMapWithSize(6));

    registry.getMetrics().keySet().forEach(metricName -> assertThat(
        "Expect all metric names to include client name:" + metricName,
        metricName.getTags(),
        hasEntry("client", "feign.metrics5.Metrics5CapabilityTest$SimpleSource")));
    registry.getMetrics().keySet().forEach(metricName -> assertThat(
        "Expect all metric names to include method name:" + metricName,
        metricName.getTags(),
        hasEntry("method", "get")));
    registry.getMetrics().keySet().forEach(metricName -> assertThat(
        "Expect all metric names to include host name:" + metricName,
        metricName.getTags(),
        // hostname is null due to feign-mock shortfalls
        hasEntry("host", null)));
  }

}
