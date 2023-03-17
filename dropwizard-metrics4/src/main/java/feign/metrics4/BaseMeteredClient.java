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
import feign.FeignException;
import feign.RequestTemplate;
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

  protected Timer.Context createTimer(RequestTemplate template) {
    return metricRegistry
        .timer(
            MetricRegistry.name(
                metricName.metricName(template.methodMetadata(), template.feignTarget()),
                "uri",
                template.methodMetadata().template().path()),
            metricSuppliers.timers())
        .time();
  }

  protected void recordSuccess(RequestTemplate template, Response response) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(template),
                "status_group",
                response.status() / 100 + "xx",
                "http_status",
                String.valueOf(response.status()),
                "http_method",
                template.methodMetadata().template().method(),
                "uri",
                template.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  protected void recordFailure(RequestTemplate template, FeignException e) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(template),
                "exception_name",
                e.getClass().getSimpleName(),
                "root_cause_name",
                ExceptionUtils.getRootCause(e).getClass().getSimpleName(),
                "status_group",
                e.status() / 100 + "xx",
                "http_status",
                String.valueOf(e.status()),
                "http_method",
                template.methodMetadata().template().method(),
                "uri",
                template.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  protected void recordFailure(RequestTemplate template, Exception e) {
    metricRegistry
        .meter(
            MetricRegistry.name(
                httpResponseCode(template),
                "exception_name",
                e.getClass().getSimpleName(),
                "root_cause_name",
                ExceptionUtils.getRootCause(e).getClass().getSimpleName(),
                "uri",
                template.methodMetadata().template().path()),
            metricSuppliers.meters())
        .mark();
  }

  private String httpResponseCode(RequestTemplate template) {
    return metricName.metricName(
        template.methodMetadata(), template.feignTarget(), "http_response_code");
  }
}
