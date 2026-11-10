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
package feign.metrics5;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.utils.ExceptionUtils;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.Timer;

public class BaseMeteredClient {

  protected final MetricRegistry metricRegistry;
  protected final FeignMetricName metricName;
  protected final MetricSuppliers metricSuppliers;

  public BaseMeteredClient(
      MetricRegistry metricRegistry, FeignMetricName metricName, MetricSuppliers metricSuppliers) {
    super();
    this.metricRegistry = metricRegistry;
    this.metricName = metricName;
    this.metricSuppliers = metricSuppliers;
  }

  protected Timer.Context createTimer(Request request) {
    return metricRegistry
        .timer(
            metricName
                .metricName(request.methodMetadata(), request.feignTarget())
                .tagged("uri", request.methodMetadata().template().path()),
            metricSuppliers.timers())
        .time();
  }

  protected void recordSuccess(Request request, Response response) {
    metricRegistry
        .counter(
            httpResponseCode(request)
                .tagged("http_status", String.valueOf(response.status()))
                .tagged("status_group", response.status() / 100 + "xx")
                .tagged("http_method", request.methodMetadata().template().method())
                .tagged("uri", request.methodMetadata().template().path()))
        .inc();
  }

  protected void recordFailure(Request request, FeignException e) {
    metricRegistry
        .counter(
            httpResponseCode(request)
                .tagged("exception_name", e.getClass().getSimpleName())
                .tagged(
                    "root_cause_name", ExceptionUtils.getRootCause(e).getClass().getSimpleName())
                .tagged("http_status", String.valueOf(e.status()))
                .tagged("status_group", e.status() / 100 + "xx")
                .tagged("http_method", request.methodMetadata().template().method())
                .tagged("uri", request.methodMetadata().template().path()))
        .inc();
  }

  protected void recordFailure(Request request, Exception e) {
    metricRegistry
        .counter(
            httpResponseCode(request)
                .tagged("exception_name", e.getClass().getSimpleName())
                .tagged(
                    "root_cause_name", ExceptionUtils.getRootCause(e).getClass().getSimpleName())
                .tagged("uri", request.methodMetadata().template().path()))
        .inc();
  }

  private MetricName httpResponseCode(Request request) {
    return metricName.metricName(
        request.methodMetadata(), request.feignTarget(), "http_response_code");
  }
}
