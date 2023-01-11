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


import java.lang.reflect.Type;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;

/**
 * Warp feign {@link Encoder} with metrics.
 */
public class MeteredEncoder implements Encoder {

  private final Encoder encoder;
  private final MetricRegistry metricRegistry;
  private final MetricSuppliers metricSuppliers;
  private final FeignMetricName metricName;

  public MeteredEncoder(Encoder encoder, MetricRegistry metricRegistry,
      MetricSuppliers metricSuppliers) {
    this.encoder = encoder;
    this.metricRegistry = metricRegistry;
    this.metricSuppliers = metricSuppliers;
    this.metricName = new FeignMetricName(Encoder.class);
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    try (final Timer.Context classTimer =
        metricRegistry.timer(
            metricName.metricName(template.methodMetadata(), template.feignTarget()),
            metricSuppliers.timers()).time()) {
      encoder.encode(object, bodyType, template);
    }

    if (template.body() != null) {
      metricRegistry.histogram(
          metricName.metricName(template.methodMetadata(), template.feignTarget(), "request_size"),
          metricSuppliers.histograms()).update(template.body().length);
    }
  }

}
