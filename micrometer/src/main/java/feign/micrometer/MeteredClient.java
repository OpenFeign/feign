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

import feign.*;
import feign.Request.Options;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Warp feign {@link Client} with metrics.
 */
public class MeteredClient implements Client {

  private final Client client;
  private final MeterRegistry meterRegistry;
  private final MetricName metricName;

  public MeteredClient(Client client, MeterRegistry meterRegistry) {
    this(client, meterRegistry, new FeignMetricName(Client.class));
  }

  public MeteredClient(Client client, MeterRegistry meterRegistry, MetricName metricName) {
    this.client = client;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final RequestTemplate template = request.requestTemplate();
    final Timer.Sample sample = Timer.start(meterRegistry);
    try {
      final Response response = client.execute(request, options);
      countSuccessResponseCode(template, options, response.status());
      final Timer timer = createSuccessTimer(template, options);
      sample.stop(timer);
      return response;
    } catch (FeignException e) {
      countErrorResponseCode(template, options, e.status(), e);
      throw e;
    } catch (IOException | RuntimeException e) {
      sample.stop(createExceptionTimer(template, options, e));
      throw e;
    } catch (Exception e) {
      sample.stop(createExceptionTimer(template, options, e));
      throw new IOException(e);
    }
  }

  private void countSuccessResponseCode(RequestTemplate template,
                                        Options options,
                                        int responseStatus) {
    final List<Tag> tags = extraSuccessTags(template, options);
    tags.add(Tag.of("http_status", String.valueOf(responseStatus)));
    tags.add(Tag.of("status_group", responseStatus / 100 + "xx"));
    final Tags allTags = metricName
        .tag(template.methodMetadata(), template.feignTarget(), tags.toArray(new Tag[] {}));
    meterRegistry.counter(
        metricName.name("http_response_code"),
        allTags)
        .increment();
  }

  private void countErrorResponseCode(RequestTemplate template,
                                      Options options,
                                      int responseStatus,
                                      Exception e) {
    final List<Tag> tags = extraExceptionTags(template, options, e);
    tags.add(Tag.of("http_status", String.valueOf(responseStatus)));
    tags.add(Tag.of("status_group", responseStatus / 100 + "xx"));
    final Tags allTags = metricName
        .tag(template.methodMetadata(), template.feignTarget(), tags.toArray(new Tag[] {}));
    meterRegistry.counter(
        metricName.name("http_response_code"),
        allTags)
        .increment();
  }

  private Timer createSuccessTimer(RequestTemplate template, Options options) {
    final List<Tag> successTags = extraSuccessTags(template, options);
    final Tag[] tags = successTags.toArray(new Tag[] {});
    final Tags allTags = metricName.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.timer(metricName.name(), allTags);
  }

  private Timer createExceptionTimer(RequestTemplate template,
                                     Options options,
                                     Exception exception) {
    final List<Tag> exceptionTags = extraExceptionTags(template, options, exception);
    final Tag[] tags = exceptionTags.toArray(new Tag[] {});
    final Tags allTags = metricName.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.timer(metricName.name("exception"), allTags);
  }

  protected List<Tag> extraSuccessTags(RequestTemplate template, Options options) {
    final List<Tag> result = new ArrayList<>();
    result.add(Tag.of("uri", template.path()));
    return result;
  }

  protected List<Tag> extraExceptionTags(RequestTemplate template,
                                         Options options,
                                         Exception exception) {
    final List<Tag> result = new ArrayList<>();
    result.add(Tag.of("uri", template.path()));
    result.add(Tag.of("exception_name", exception.getClass().getSimpleName()));
    return result;
  }
}
