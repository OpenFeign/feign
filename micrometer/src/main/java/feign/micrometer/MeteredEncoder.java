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

import static feign.Util.CONTENT_LENGTH;
import static feign.micrometer.MetricTagResolver.EMPTY_TAGS_ARRAY;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.Type;
import java.util.Collections;

/** Wrap feign {@link Encoder} with metrics. */
public class MeteredEncoder implements Encoder {

  private final Encoder encoder;
  private final MeterRegistry meterRegistry;
  private final MetricName metricName;
  private final MetricTagResolver metricTagResolver;

  public MeteredEncoder(Encoder encoder, MeterRegistry meterRegistry) {
    this(encoder, meterRegistry, new FeignMetricName(Encoder.class), new FeignMetricTagResolver());
  }

  public MeteredEncoder(
      Encoder encoder,
      MeterRegistry meterRegistry,
      MetricName metricName,
      MetricTagResolver metricTagResolver) {
    this.encoder = encoder;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
    this.metricTagResolver = metricTagResolver;
  }

  @Override
  public boolean encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    boolean isEncoded =
        createTimer(object, bodyType, template)
            .record(() -> encoder.encode(object, bodyType, template));

    template.headers().getOrDefault(CONTENT_LENGTH, Collections.emptySet()).stream()
        .findFirst()
        .ifPresent(
            contentLength ->
                createSummary(object, bodyType, template).record(Long.parseLong(contentLength)));
    return isEncoded;
  }

  protected Timer createTimer(Object object, Type bodyType, RequestTemplate template) {
    final Tags allTags =
        metricTagResolver.tag(
            template.methodMetadata(),
            template.feignTarget(),
            extraTags(object, bodyType, template));
    return meterRegistry.timer(metricName.name(), allTags);
  }

  protected DistributionSummary createSummary(
      Object object, Type bodyType, RequestTemplate template) {
    final Tags allTags =
        metricTagResolver.tag(
            template.methodMetadata(),
            template.feignTarget(),
            extraTags(object, bodyType, template));
    return meterRegistry.summary(metricName.name("response_size"), allTags);
  }

  protected Tag[] extraTags(Object object, Type bodyType, RequestTemplate template) {
    return EMPTY_TAGS_ARRAY;
  }
}
