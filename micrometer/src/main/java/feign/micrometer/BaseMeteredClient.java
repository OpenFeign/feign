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
package feign.micrometer;

import static feign.micrometer.MetricTagResolver.EMPTY_TAGS_ARRAY;
import feign.Request;
import feign.Request.Options;
import feign.RequestTemplate;
import feign.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

abstract class BaseMeteredClient {

  protected final MeterRegistry meterRegistry;
  protected final MetricName metricName;
  protected final MetricTagResolver metricTagResolver;

  public BaseMeteredClient(
      MeterRegistry meterRegistry, MetricName metricName, MetricTagResolver metricTagResolver) {
    super();
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
    this.metricTagResolver = metricTagResolver;
  }

  protected void countResponseCode(
                                   Request request,
                                   Response response,
                                   Options options,
                                   int responseStatus,
                                   Exception e) {
    final Tag[] extraTags = extraTags(request, response, options, e);
    final RequestTemplate template = request.requestTemplate();
    final Tags allTags =
        metricTagResolver
            .tag(
                template.methodMetadata(),
                template.feignTarget(),
                e,
                Tag.of("http_status", String.valueOf(responseStatus)),
                Tag.of("status_group", responseStatus / 100 + "xx"),
                Tag.of("http_method", template.methodMetadata().template().method()),
                Tag.of("uri", template.methodMetadata().template().path()))
            .and(extraTags);
    meterRegistry.counter(metricName.name("http_response_code"), allTags).increment();
  }

  protected Timer createTimer(Request request, Response response, Options options, Exception e) {
    final RequestTemplate template = request.requestTemplate();
    final Tags allTags =
        metricTagResolver
            .tag(
                template.methodMetadata(),
                template.feignTarget(),
                e,
                Tag.of("uri", template.methodMetadata().template().path()))
            .and(extraTags(request, response, options, e));
    return meterRegistry.timer(metricName.name(e), allTags);
  }

  protected Tag[] extraTags(Request request, Response response, Options options, Exception e) {
    return EMPTY_TAGS_ARRAY;
  }
}
