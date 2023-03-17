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
package feign.metrics5;

import feign.AsyncClient;
import feign.FeignException;
import feign.RequestTemplate;
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

  protected Timer.Context createTimer(RequestTemplate template) {
    return metricRegistry
        .timer(
            metricName
                .metricName(template.methodMetadata(), template.feignTarget())
                .tagged("uri", template.methodMetadata().template().path()),
            metricSuppliers.timers())
        .time();
  }

  protected void recordSuccess(RequestTemplate template, Response response) {
    metricRegistry
        .counter(
            httpResponseCode(template)
                .tagged("http_status", String.valueOf(response.status()))
                .tagged("status_group", response.status() / 100 + "xx")
                .tagged("http_method", template.methodMetadata().template().method())
                .tagged("uri", template.methodMetadata().template().path()))
        .inc();
  }

  protected void recordFailure(RequestTemplate template, FeignException e) {
    metricRegistry
        .counter(
            httpResponseCode(template)
                .tagged("exception_name", e.getClass().getSimpleName())
                .tagged("root_cause_name",
                    ExceptionUtils.getRootCause(e).getClass().getSimpleName())
                .tagged("http_status", String.valueOf(e.status()))
                .tagged("status_group", e.status() / 100 + "xx")
                .tagged("http_method", template.methodMetadata().template().method())
                .tagged("uri", template.methodMetadata().template().path()))
        .inc();
  }

  protected void recordFailure(RequestTemplate template, Exception e) {
    metricRegistry
        .counter(
            httpResponseCode(template)
                .tagged("exception_name", e.getClass().getSimpleName())
                .tagged("root_cause_name",
                    ExceptionUtils.getRootCause(e).getClass().getSimpleName())
                .tagged("uri", template.methodMetadata().template().path()))
        .inc();
  }

  private MetricName httpResponseCode(RequestTemplate template) {
    return metricName.metricName(
        template.methodMetadata(), template.feignTarget(), "http_response_code");
  }
}
