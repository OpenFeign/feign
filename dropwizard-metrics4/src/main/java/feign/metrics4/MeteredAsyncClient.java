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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import feign.AsyncClient;
import feign.FeignException;
import feign.Request;
import feign.Request.Options;
import feign.RequestTemplate;
import feign.Response;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Warp feign {@link AsyncClient} with metrics. */
public class MeteredAsyncClient extends BaseMeteredClient implements AsyncClient<Object> {

  private final AsyncClient<Object> asyncClient;

  public MeteredAsyncClient(
      AsyncClient<Object> asyncClient,
      MetricRegistry metricRegistry,
      MetricSuppliers metricSuppliers) {
    super(metricRegistry, new FeignMetricName(AsyncClient.class), metricSuppliers);
    this.asyncClient = asyncClient;
  }

  @Override
  public CompletableFuture<Response> execute(
                                             Request request,
                                             Options options,
                                             Optional<Object> requestContext) {
    final RequestTemplate template = request.requestTemplate();
    final Timer.Context timer = createTimer(template);
    return asyncClient
        .execute(request, options, requestContext)
        .whenComplete(
            (response, th) -> {
              if (th == null) {
                recordSuccess(template, response);
              } else if (th instanceof FeignException) {
                FeignException e = (FeignException) th;
                recordFailure(template, e);
              } else if (th instanceof Exception) {
                Exception e = (Exception) th;
                recordFailure(template, e);
              }
            })
        .whenComplete((response, th) -> timer.close());
  }
}
