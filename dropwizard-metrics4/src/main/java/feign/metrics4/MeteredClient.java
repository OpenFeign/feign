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
import feign.Client;
import feign.FeignException;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import java.io.IOException;

/** Warp feign {@link Client} with metrics. */
public class MeteredClient extends BaseMeteredClient implements Client {

  private final Client client;

  public MeteredClient(
      Client client, MetricRegistry metricRegistry, MetricSuppliers metricSuppliers) {
    super(metricRegistry, new FeignMetricName(Client.class), metricSuppliers);
    this.client = client;
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    try (final Timer.Context classTimer = createTimer(request)) {
      Response response = client.execute(request, options);
      recordSuccess(request, response);
      return response;
    } catch (FeignException e) {
      recordFailure(request, e);
      throw e;
    } catch (IOException | RuntimeException e) {
      recordFailure(request, e);
      throw e;
    } catch (Exception e) {
      recordFailure(request, e);
      throw new IOException(e);
    }
  }
}
