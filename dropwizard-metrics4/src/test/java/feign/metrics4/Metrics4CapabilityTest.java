/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.metrics4;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import feign.Capability;
import feign.Util;
import feign.micrometer.AbstractMetricsTestBase;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import org.hamcrest.Matcher;

public class Metrics4CapabilityTest
    extends AbstractMetricsTestBase<MetricRegistry, String, Metric> {

  @Override
  protected MetricRegistry createMetricsRegistry() {
    return new MetricRegistry();
  }

  @Override
  protected Capability createMetricCapability() {
    return new Metrics4Capability(metricsRegistry);
  }

  @Override
  protected Map<String, Metric> getFeignMetrics() {
    return metricsRegistry.getMetrics();
  }

  @Override
  protected boolean doesMetricIdIncludeClient(String metricId) {
    Matcher<String> containsBase = containsString("feign.micrometer.AbstractMetricsTestBase$");
    Matcher<String> containsSource = containsString("Source");
    return allOf(containsBase, containsSource).matches(metricId);
  }

  @Override
  protected boolean doesMetricIncludeVerb(String metricId, String verb) {
    return metricId.contains(verb);
  }

  @Override
  protected boolean doesMetricIncludeHost(String metricId) {
    // since metrics 4 don't have tags, we do not include hostname
    return true;
  }

  @Override
  protected Metric getMetric(String suffix, String... tags) {
    Util.checkArgument(
        tags.length % 2 == 0, "tags must contain key-value pairs %s", Arrays.toString(tags));

    return getFeignMetrics().entrySet().stream()
        .filter(
            entry -> {
              String name = entry.getKey();
              if (!name.contains(suffix)) {
                return false;
              }

              for (int i = 0; i < tags.length; i += 2) {
                // metrics 4 doesn't support tags, for that reason we don't include tag name
                if (!name.contains(tags[i + 1])) {
                  return false;
                }
              }

              return true;
            })
        .findAny()
        .map(Entry::getValue)
        .orElse(null);
  }

  @Override
  protected boolean isClientMetric(String metricId) {
    return metricId.startsWith("feign.Client");
  }

  @Override
  protected boolean isAsyncClientMetric(String metricId) {
    return metricId.startsWith("feign.AsyncClient");
  }

  @Override
  protected boolean isDecoderMetric(String metricId) {
    return metricId.startsWith("feign.codec.Decoder");
  }

  @Override
  protected boolean doesMetricIncludeUri(String metricId, String uri) {
    return metricId.contains(uri);
  }

  @Override
  protected boolean doesMetricHasCounter(Metric metric) {
    return metric instanceof Metered;
  }

  @Override
  protected long getMetricCounter(Metric metric) {
    return ((Metered) metric).getCount();
  }
}
