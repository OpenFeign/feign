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
package feign.micrometer;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import feign.Feign;
import feign.RequestLine;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class MicrometerCapabilityTest {

  public interface SimpleSource {

    @RequestLine("GET /get")
    String get(String body);

  }

  @Test
  public void addMetricsCapability() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    final SimpleSource source = Feign.builder()
        .client(new MockClient()
            .ok(HttpMethod.GET, "/get", "1234567890abcde"))
        .addCapability(new MicrometerCapability(registry))
        .target(new MockTarget<>(MicrometerCapabilityTest.SimpleSource.class));

    source.get("0x3456789");

    List<Meter> metrics = new ArrayList<>();
    registry.forEachMeter(metrics::add);
    metrics.removeIf(meter -> !meter.getId().getName().startsWith("feign."));

    metrics.forEach(meter -> assertThat(
        "Expect all metric names to include client name:" + meter.getId(),
        meter.getId().getTag("client"),
        equalTo("feign.micrometer.MicrometerCapabilityTest$SimpleSource")));
    metrics.forEach(meter -> assertThat(
        "Expect all metric names to include method name:" + meter.getId(),
        meter.getId().getTag("method"),
        equalTo("get")));
    metrics.forEach(meter -> assertThat(
        "Expect all metric names to include host name:" + meter.getId(),
        meter.getId().getTag("host"),
        // hostname is blank due to feign-mock shortfalls
        equalTo("")));
  }

}
