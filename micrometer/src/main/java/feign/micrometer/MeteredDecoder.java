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


import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;
import feign.FeignException;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

/**
 * Warp feign {@link Decoder} with metrics.
 */
public class MeteredDecoder implements Decoder {

  private final Decoder decoder;
  private final MeterRegistry meterRegistry;
  private final FeignMetricName metricName;

  public MeteredDecoder(Decoder decoder, MeterRegistry meterRegistry) {
    this.decoder = decoder;
    this.meterRegistry = meterRegistry;
    this.metricName = new FeignMetricName(Decoder.class);
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {
    final RequestTemplate template = response.request().requestTemplate();
    final Optional<MeteredBody> body = Optional.ofNullable(response.body())
        .map(MeteredBody::new);

    Response meteredResponse = body.map(b -> response.toBuilder().body(b).build())
        .orElse(response);

    Object decoded;
    try {
      decoded = meterRegistry
          .timer(metricName.name(),
              metricName.tag(template.methodMetadata(), template.feignTarget()))
          .recordCallable(() -> decoder.decode(meteredResponse, type));
    } catch (IOException | RuntimeException e) {
      meterRegistry.counter(
          metricName.name("error_count"),
          metricName.tag(template.methodMetadata(), template.feignTarget())
              .and(Tag.of("exception_name", e.getClass().getSimpleName())))
          .count();
      throw e;
    } catch (Exception e) {
      meterRegistry.counter(
          metricName.name("error_count"),
          metricName.tag(template.methodMetadata(), template.feignTarget())
              .and(Tag.of("exception_name", e.getClass().getSimpleName())))
          .count();
      throw new IOException(e);
    }

    body.ifPresent(b -> meterRegistry.summary(
        metricName.name("response_size"),
        metricName.tag(template.methodMetadata(), template.feignTarget())).record(b.count()));

    return decoded;
  }

}
