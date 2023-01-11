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
package feign.metrics5;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import feign.Capability;
import feign.Util;
import feign.micrometer.AbstractMetricsTestBase;
import io.dropwizard.metrics5.Metered;
import io.dropwizard.metrics5.Metric;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import org.hamcrest.Matcher;

public class Metrics5CapabilityTest
    extends AbstractMetricsTestBase<MetricRegistry, MetricName, Metric> {

  @Override
  protected MetricRegistry createMetricsRegistry() {
    return new MetricRegistry();
  }

  @Override
  protected Capability createMetricCapability() {
    return new Metrics5Capability(metricsRegistry);
  }

  @Override
  protected Map<MetricName, Metric> getFeignMetrics() {
    return metricsRegistry.getMetrics();
  }

  @Override
  protected boolean doesMetricIdIncludeClient(MetricName metricId) {
    String tag = metricId.getTags().get("client");
    Matcher<String> containsBase = containsString("feign.micrometer.AbstractMetricsTestBase$");
    Matcher<String> containsSource = containsString("Source");
    return allOf(containsBase, containsSource).matches(tag);
  }

  @Override
  protected boolean doesMetricIncludeVerb(MetricName metricId, String verb) {
    return hasEntry("method", verb).matches(metricId.getTags());
  }

  @Override
  protected boolean doesMetricIncludeHost(MetricName metricId) {
    // hostname is null due to feign-mock shortfalls
    return hasEntry("host", null).matches(metricId.getTags());
  }

  @Override
  protected Metric getMetric(String suffix, String... tags) {
    Util.checkArgument(
        tags.length % 2 == 0, "tags must contain key-value pairs %s", Arrays.toString(tags));

    return getFeignMetrics().entrySet().stream()
        .filter(
            entry -> {
              MetricName name = entry.getKey();
              if (!name.getKey().endsWith(suffix)) {
                return false;
              }

              for (int i = 0; i < tags.length; i += 2) {
                if (name.getTags().containsKey(tags[i])) {
                  if (!name.getTags().get(tags[i]).equals(tags[i + 1])) {
                    return false;
                  }
                }
              }

              return true;
            })
        .findAny()
        .map(Entry::getValue)
        .orElse(null);
  }

  @Override
  protected boolean isClientMetric(MetricName metricId) {
    return metricId.getKey().startsWith("feign.Client");
  }

  @Override
  protected boolean isAsyncClientMetric(MetricName metricId) {
    return metricId.getKey().startsWith("feign.AsyncClient");
  }

  @Override
  protected boolean isDecoderMetric(MetricName metricId) {
    return metricId.getKey().startsWith("feign.codec.Decoder");
  }

  @Override
  protected boolean doesMetricIncludeUri(MetricName metricId, String uri) {
    return uri.equals(metricId.getTags().get("uri"));
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
