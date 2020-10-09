/**
 * Copyright 2012-2020 The Feign Authors
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

import java.io.IOException;
import feign.Client;
import feign.Request;
import feign.Request.Options;
import feign.RequestTemplate;
import feign.Response;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Warp feign {@link Client} with metrics.
 */
public class MeteredClient implements Client {

  private final Client client;
  private final MeterRegistry meterRegistry;
  private final FeignMetricName metricName;

  public MeteredClient(Client client, MeterRegistry meterRegistry) {
    this.client = client;
    this.meterRegistry = meterRegistry;
    this.metricName = new FeignMetricName(Client.class);
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final RequestTemplate template = request.requestTemplate();

    try {
      return meterRegistry.timer(
          metricName.name(),
          metricName.tag(template.methodMetadata(), template.feignTarget()))
          .recordCallable(() -> client.execute(request, options));
    } catch (IOException | RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

}
