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

import feign.Client;
import feign.FeignException;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;

/** Wrap feign {@link Client} with metrics. */
public class MeteredClient extends BaseMeteredClient implements Client {

  private final Client client;

  public MeteredClient(Client client, MeterRegistry meterRegistry) {
    this(client, meterRegistry, new FeignMetricName(Client.class), new FeignMetricTagResolver());
  }

  public MeteredClient(
      Client client,
      MeterRegistry meterRegistry,
      MetricName metricName,
      MetricTagResolver metricTagResolver) {
    super(meterRegistry, metricName, metricTagResolver);
    this.client = client;
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final Timer.Sample sample = Timer.start(meterRegistry);
    Timer timer = null;
    try {
      final Response response = client.execute(request, options);
      countResponseCode(request, response, options, response.status(), null);
      timer = createTimer(request, response, options, null);
      return response;
    } catch (FeignException e) {
      timer = createTimer(request, null, options, e);
      countResponseCode(request, null, options, e.status(), e);
      throw e;
    } catch (IOException | RuntimeException e) {
      timer = createTimer(request, null, options, e);
      throw e;
    } catch (Exception e) {
      timer = createTimer(request, null, options, e);
      throw new IOException(e);
    } finally {
      if (timer == null) {
        timer = createTimer(request, null, options, null);
      }
      sample.stop(timer);
    }
  }
}
