/**
 * Copyright 2012-2021 The Feign Authors
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import feign.Capability;
import feign.Util;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class MicrometerCapabilityTest
    extends AbstractMetricsTestBase<SimpleMeterRegistry, Id, Meter> {

  @Override
  protected SimpleMeterRegistry createMetricsRegistry() {
    return new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
  }

  protected Capability createMetricCapability() {
    return new MicrometerCapability(metricsRegistry);
  }

  @Override
  protected Map<Id, Meter> getFeignMetrics() {
    List<Meter> metrics = new ArrayList<>();
    metricsRegistry.forEachMeter(metrics::add);
    metrics.removeIf(meter -> !meter.getId().getName().startsWith("feign."));
    return metrics.stream()
        .collect(Collectors.toMap(
            Meter::getId,
            Function.identity()));
  }

  @Override
  protected boolean doesMetricIdIncludeClient(Id metricId) {
    return metricId.getTag("client")
        .contains("feign.micrometer.AbstractMetricsTestBase$SimpleSource");
  }

  @Override
  protected boolean doesMetricIncludeVerb(Id metricId, String verb) {
    return metricId.getTag("method").equals(verb);
  }

  @Override
  protected boolean doesMetricIncludeHost(Id metricId) {
    return metricId.getTag("host").equals("");
  }


  @Override
  protected Meter getMetric(String suffix, String... tags) {
    Util.checkArgument(tags.length % 2 == 0, "tags must contain key-value pairs %s",
        Arrays.toString(tags));


    return getFeignMetrics().entrySet()
        .stream()
        .filter(entry -> {
          Id name = entry.getKey();
          if (!name.getName().endsWith(suffix)) {
            return false;
          }

          for (int i = 0; i < tags.length; i += 2) {
            if (name.getTag(tags[i]) != null) {
              if (!name.getTag(tags[i]).equals(tags[i + 1])) {
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


}
