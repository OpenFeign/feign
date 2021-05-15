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

import feign.Client;
import feign.FeignException;
import feign.Request;
import feign.Request.Options;
import feign.RequestTemplate;
import feign.Response;
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
    final Timer.Sample sample = Timer.start(meterRegistry);
    try {
      meterRegistry.counter(
              metricName.name("http_response_code"),
              metricName.tag(
                      template.methodMetadata(),
                      template.feignTarget(),
                      Tag.of("http_status", String.valueOf(response.status())),
                      Tag.of("status_group", response.status() / 100 + "xx")))
              .increment();
      final Response response = client.execute(request, options);

      final Timer timer = createSuccessTimer(response, options);
      sample.stop(timer);
      return response;
    } catch (FeignException e) {
      meterRegistry.counter(
          metricName.name("http_response_code"),
          metricName.tag(
              template.methodMetadata(),
              template.feignTarget(),
              Tag.of("http_status", String.valueOf(e.status())),
              Tag.of("status_group", e.status() / 100 + "xx")))
          .increment();
      throw e;
    } catch (IOException | RuntimeException e) {
      sample.stop(createExceptionTimer(request, options, e));
      throw e;
    } catch (Exception e) {
      sample.stop(createExceptionTimer(request, options, e));
      throw new IOException(e);
    }
  }

  private Timer createSuccessTimer(Response response, Options options) {
    final List<Tag> successTags = extraSuccessTags(response, options);
    final Tag[] tags = successTags.toArray(new Tag[] {});
    final RequestTemplate template = response.request().requestTemplate();
    final Tags allTags = metricName.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.timer(metricName.name(), allTags);
  }

  private Timer createExceptionTimer(Request request, Options options, Exception exception) {
    final List<Tag> exceptionTags = extraExceptionTags(request, options, exception);
    final Tag[] tags = exceptionTags.toArray(new Tag[] {});
    final RequestTemplate template = request.requestTemplate();
    final Tags allTags = metricName.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.timer(metricName.name("exception"), allTags);
  }

  protected List<Tag> extraSuccessTags(Response response, Options options) {
    final RequestTemplate template = response.request().requestTemplate();
    final List<Tag> result = new ArrayList<>();
    result.add(Tag.of("uri", template.path()));
    return result;
  }

  protected List<Tag> extraExceptionTags(Request request, Options options, Exception exception) {
    final RequestTemplate template = request.requestTemplate();
    final List<Tag> result = new ArrayList<>();
    result.add(Tag.of("uri", template.path()));
    result.add(Tag.of("exception_name", exception.getClass().getSimpleName()));
    return result;
  }
}
