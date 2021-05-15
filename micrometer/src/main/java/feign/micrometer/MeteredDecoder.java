/**
 * Copyright 2012-2021 The Feign Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.micrometer;


import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import io.micrometer.core.instrument.*;

/**
 * Warp feign {@link Decoder} with metrics.
 */
public class MeteredDecoder implements Decoder {

  private final Decoder decoder;
  private final MeterRegistry meterRegistry;
  private final MetricName metricName;

  public MeteredDecoder(Decoder decoder, MeterRegistry meterRegistry) {
    this(decoder, meterRegistry, new FeignMetricName(Decoder.class));
  }

  public MeteredDecoder(Decoder decoder, MeterRegistry meterRegistry, MetricName metricName) {
    this.decoder = decoder;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, FeignException {
    final Optional<MeteredBody> body = Optional.ofNullable(response.body())
        .map(MeteredBody::new);

    Response meteredResponse = body.map(b -> response.toBuilder().body(b).build())
        .orElse(response);

    Object decoded;

    final Timer.Sample sample = Timer.start(meterRegistry);
    try {
      decoded = decoder.decode(meteredResponse, type);
      final Timer timer = createSuccessTimer(response, type);
      sample.stop(timer);
    } catch (IOException | RuntimeException e) {
      sample.stop(createExceptionTimer(response, type, e));
      createExceptionCounter(response, type, e).count();
      throw e;
    } catch (Exception e) {
      sample.stop(createExceptionTimer(response, type, e));
      createExceptionCounter(response, type, e).count();
      throw new IOException(e);
    }

    body.ifPresent(b -> createSummary(response, type).record(b.count()));

    return decoded;
  }

  private Timer createSuccessTimer(Response response, Type type) {
    final List<Tag> successTags = extraSuccessTags(response, type);
    final Tag[] tags = successTags.toArray(new Tag[] {});
    final RequestTemplate template = response.request().requestTemplate();
    final Tags allTags = metricName.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.timer(metricName.name(), allTags);
  }

  private Timer createExceptionTimer(Response response, Type type, Exception exception) {
    final List<Tag> exceptionTags = extraExceptionTags(response, type, exception);
    final Tag[] tags = exceptionTags.toArray(new Tag[] {});
    final RequestTemplate template = response.request().requestTemplate();
    final Tags allTags = metricName.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.timer(metricName.name("exception"), allTags);
  }

  private Counter createExceptionCounter(Response response, Type type, Exception exception) {
    final List<Tag> exceptionTags = extraExceptionTags(response, type, exception);
    final Tag[] tags = exceptionTags.toArray(new Tag[] {});
    final RequestTemplate template = response.request().requestTemplate();
    final Tags allTags = metricName.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.counter(metricName.name("error_count"), allTags);
  }

  private DistributionSummary createSummary(Response response, Type type) {
    final List<Tag> successTags = extraSummaryTags(response, type);
    final Tag[] tags = successTags.toArray(new Tag[] {});
    final RequestTemplate template = response.request().requestTemplate();
    final Tags allTags = metricName.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.summary(metricName.name("response_size"), allTags);
  }

  protected List<Tag> extraSuccessTags(Response response, Type type) {
    return Collections.emptyList();
  }

  protected List<Tag> extraExceptionTags(Response response, Type type, Exception exception) {
    final RequestTemplate template = response.request().requestTemplate();
    final List<Tag> result = new ArrayList<>();
    result.add(Tag.of("uri", template.path()));
    result.add(Tag.of("exception_name", exception.getClass().getSimpleName()));
    return result;
  }

  protected List<Tag> extraSummaryTags(Response response, Type type) {
    return Collections.emptyList();
  }

}
