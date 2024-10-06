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
import feign.Capability;
import feign.Client;
import feign.FeignException;
import feign.Response;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/** Wrap feign {@link Client} with metrics. */
public class MicrometerObservationCapability implements Capability {

  private final ObservationRegistry observationRegistry;

  private final FeignObservationConvention customFeignObservationConvention;

  public MicrometerObservationCapability(ObservationRegistry observationRegistry,
      FeignObservationConvention customFeignObservationConvention) {
    this.observationRegistry = observationRegistry;
    this.customFeignObservationConvention = customFeignObservationConvention;
  }

  public MicrometerObservationCapability(ObservationRegistry observationRegistry) {
    this(observationRegistry, null);
  }

  @Override
  public Client enrich(Client client) {
    return (request, options) -> {
      FeignContext feignContext = new FeignContext(request);

      Observation observation = FeignObservationDocumentation.DEFAULT
          .observation(this.customFeignObservationConvention,
              DefaultFeignObservationConvention.INSTANCE, () -> feignContext,
              this.observationRegistry)
          .start();

      try {
        Response response = client.execute(request, options);
        finalizeObservation(feignContext, observation, null, response);
        return response;
      } catch (FeignException ex) {
        finalizeObservation(feignContext, observation, ex, null);
        throw ex;
      }
    };
  }

  @Override
  public AsyncClient<Object> enrich(AsyncClient<Object> client) {
    return (request, options, context) -> {
      FeignContext feignContext = new FeignContext(request);

      Observation observation = FeignObservationDocumentation.DEFAULT
          .observation(this.customFeignObservationConvention,
              DefaultFeignObservationConvention.INSTANCE, () -> feignContext,
              this.observationRegistry)
          .start();

      try {
        return client.execute(feignContext.getCarrier(), options, context)
            .whenComplete((r, ex) -> finalizeObservation(feignContext, observation, ex, r));
      } catch (FeignException ex) {
        finalizeObservation(feignContext, observation, ex, null);

        throw ex;
      }
    };
  }

  private void finalizeObservation(FeignContext feignContext,
                                   Observation observation,
                                   Throwable ex,
                                   Response response) {
    feignContext.setResponse(response);
    if (ex != null) {
      observation.error(ex);
    }
    observation.stop();
  }
}
