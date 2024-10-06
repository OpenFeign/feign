/*
 * Copyright 2012-2024 The Feign Authors
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

import feign.AsyncClient;
import feign.Client;
import feign.FeignException;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Wrap feign {@link Client} with metrics. */
public class MeteredAsyncClient extends BaseMeteredClient implements AsyncClient<Object> {

  private final AsyncClient<Object> client;

  public MeteredAsyncClient(AsyncClient<Object> client, MeterRegistry meterRegistry) {
    this(
        client,
        meterRegistry,
        new FeignMetricName(AsyncClient.class),
        new FeignMetricTagResolver());
  }

  public MeteredAsyncClient(
      AsyncClient<Object> client,
      MeterRegistry meterRegistry,
      MetricName metricName,
      MetricTagResolver metricTagResolver) {
    super(meterRegistry, metricName, metricTagResolver);
    this.client = client;
  }

  @Override
  public CompletableFuture<Response> execute(
                                             Request request,
                                             Options options,
                                             Optional<Object> requestContext) {
    final Timer.Sample sample = Timer.start(meterRegistry);
    return client
        .execute(request, options, requestContext)
        .whenComplete(
            (response, th) -> {
              Timer timer;
              if (th == null) {
                countResponseCode(request, response, options, response.status(), null);
                timer = createTimer(request, response, options, null);
              } else if (th instanceof FeignException) {
                FeignException e = (FeignException) th;
                timer = createTimer(request, response, options, e);
                countResponseCode(request, response, options, e.status(), e);
              } else if (th instanceof Exception) {
                Exception e = (Exception) th;
                timer = createTimer(request, response, options, e);
              } else {
                timer = createTimer(request, response, options, null);
              }
              sample.stop(timer);
            });
  }
}
