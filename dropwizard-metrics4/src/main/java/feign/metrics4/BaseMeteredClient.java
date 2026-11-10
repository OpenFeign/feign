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
package feign.metrics4;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.utils.ExceptionUtils;

class BaseMeteredClient {

  protected final MetricRegistry metricRegistry;
  protected final FeignMetricName metricName;
  protected final MetricSuppliers metricSuppliers;

  public BaseMeteredClient(
      MetricRegistry metricRegistry, FeignMetricName metricName, MetricSuppliers metricSuppliers) {
    this.metricRegistry = metricRegistry;
    this.metricName = metricName;
    this.metricSuppliers = metricSuppliers;
  }

  protected Timer.Context createTimer(Request request) {
    return metricRegistry
        .timer(
            MetricRegistry.name(
                metricName.metricName(request.methodMetadata(), request.feignTarget()),
                "uri",
                request.methodMetadata().template().path()),
            metricSuppliers.timers())
        .time();
  }

  protected void recordSuccess(Request request, Response response) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(request),
                "status_group",
                response.status() / 100 + "xx",
                "http_status",
                String.valueOf(response.status()),
                "http_method",
                request.methodMetadata().template().method(),
                "uri",
                request.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  protected void recordFailure(Request request, FeignException e) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(request),
                "exception_name",
                e.getClass().getSimpleName(),
                "root_cause_name",
                ExceptionUtils.getRootCause(e).getClass().getSimpleName(),
                "status_group",
                e.status() / 100 + "xx",
                "http_status",
                String.valueOf(e.status()),
                "http_method",
                request.methodMetadata().template().method(),
                "uri",
                request.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  protected void recordFailure(Request request, Exception e) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(request),
                "exception_name",
                e.getClass().getSimpleName(),
                "root_cause_name",
                ExceptionUtils.getRootCause(e).getClass().getSimpleName(),
                "uri",
                request.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  private String httpResponseCode(Request request) {
    return metricName.metricName(
        request.methodMetadata(), request.feignTarget(), "http_response_code");
  }
}
