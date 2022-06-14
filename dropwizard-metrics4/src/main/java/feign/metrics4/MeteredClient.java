/*
 * Copyright 2012-2022 The Feign Authors
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

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import feign.*;
import feign.Request.Options;

/**
 * Warp feign {@link Client} with metrics.
 */
public class MeteredClient implements Client, AsyncClient<Object> {

  private Client delegate;
  private AsyncClient<Object> asyncDelegate;
  private final MetricRegistry metricRegistry;
  private final FeignMetricName metricName;
  private final MetricSuppliers metricSuppliers;

  public MeteredClient(Client client, MetricRegistry metricRegistry,
      MetricSuppliers metricSuppliers) {
    this.delegate = client;
    this.metricRegistry = metricRegistry;
    this.metricSuppliers = metricSuppliers;
    this.metricName = new FeignMetricName(Client.class);
  }

  public MeteredClient(AsyncClient<Object> asyncClient, MetricRegistry metricRegistry,
      MetricSuppliers metricSuppliers) {
    this.asyncDelegate = asyncClient;
    this.metricRegistry = metricRegistry;
    this.metricSuppliers = metricSuppliers;
    this.metricName = new FeignMetricName(Client.class);
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final RequestTemplate template = request.requestTemplate();
    try (final Timer.Context classTimer = createTimer(template)) {
      Response response = delegate.execute(request, options);
      recordSuccess(template, response);
      return response;
    } catch (FeignException e) {
      recordFailure(template, e);
      throw e;
    } catch (IOException | RuntimeException e) {
      recordFailure(template, e);
      throw e;
    } catch (Exception e) {
      recordFailure(template, e);
      throw new IOException(e);
    }
  }

  @Override
  public CompletableFuture<Response> execute(Request request,
                                             Options options,
                                             Optional<Object> requestContext) {
    final RequestTemplate template = request.requestTemplate();
    final Timer.Context timer = createTimer(template);
    return asyncDelegate.execute(request, options, requestContext)
        .whenComplete((response, th) -> {
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

  private Timer.Context createTimer(RequestTemplate template) {
    return metricRegistry
        .timer(
            MetricRegistry.name(
                metricName.metricName(template.methodMetadata(), template.feignTarget()),
                "uri", template.methodMetadata().template().path()),
            metricSuppliers.timers())
        .time();
  }

  private void recordSuccess(RequestTemplate template, Response response) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(template),
                "status_group", response.status() / 100 + "xx",
                "http_status", String.valueOf(response.status()),
                "uri", template.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  private void recordFailure(RequestTemplate template, FeignException e) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(template),
                "exception_name", e.getClass().getSimpleName(),
                "status_group", e.status() / 100 + "xx",
                "http_status", String.valueOf(e.status()),
                "uri", template.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  private void recordFailure(RequestTemplate template, Exception e) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(template),
                "exception_name", e.getClass().getSimpleName(),
                "uri", template.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  private String httpResponseCode(RequestTemplate template) {
    return metricName.metricName(template.methodMetadata(), template.feignTarget(),
        "http_response_code");
  }

}
