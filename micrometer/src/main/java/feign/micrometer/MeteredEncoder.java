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


import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Type;

/**
 * Warp feign {@link Encoder} with metrics.
 */
public class MeteredEncoder implements Encoder {

  private final Encoder encoder;
  private final MeterRegistry meterRegistry;
  private final MetricName metricName;

  public MeteredEncoder(Encoder encoder, MeterRegistry meterRegistry) {
    this(encoder, meterRegistry, new FeignMetricName(Encoder.class));
  }

  public MeteredEncoder(Encoder encoder, MeterRegistry meterRegistry, MetricName metricName) {
    this.encoder = encoder;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    meterRegistry.timer(
        metricName.name(),
        metricName.tag(template.methodMetadata(), template.feignTarget()))
        .record(() -> encoder.encode(object, bodyType, template));

    if (template.body() != null) {
      meterRegistry.summary(
          metricName.name("request_size"),
          metricName.tag(template.methodMetadata(), template.feignTarget()))
          .record(template.body().length);
    }
  }

}
