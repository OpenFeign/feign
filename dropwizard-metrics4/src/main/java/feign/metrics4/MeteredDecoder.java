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
package feign.metrics4;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import feign.FeignException;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;

/** Warp feign {@link Decoder} with metrics. */
public class MeteredDecoder implements Decoder {

  private final Decoder decoder;
  private final MetricRegistry metricRegistry;
  private final MetricSuppliers metricSuppliers;
  private final FeignMetricName metricName;

  public MeteredDecoder(
      Decoder decoder, MetricRegistry metricRegistry, MetricSuppliers metricSuppliers) {
    this.decoder = decoder;
    this.metricRegistry = metricRegistry;
    this.metricSuppliers = metricSuppliers;
    this.metricName = new FeignMetricName(Decoder.class);
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {
    final RequestTemplate template = response.request().requestTemplate();
    final MeteredBody body = response.body() == null ? null : new MeteredBody(response.body());

    response = response.toBuilder().body(body).build();

    final Object decoded;
    try (final Timer.Context classTimer =
        metricRegistry
            .timer(
                MetricRegistry.name(
                    metricName.metricName(template.methodMetadata(), template.feignTarget()),
                    "uri",
                    template.methodMetadata().template().path()),
                metricSuppliers.timers())
            .time()) {
      decoded = decoder.decode(response, type);
    }

    if (body != null) {
      metricRegistry
          .histogram(
              metricName.metricName(
                  template.methodMetadata(), template.feignTarget(), "response_size"),
              metricSuppliers.histograms())
          .update(body.count());
    }

    return decoded;
  }
}
