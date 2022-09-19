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
package feign.micrometer;

import feign.Client;
import feign.ClientInterceptor;
import feign.ClientInvocationContext;
import feign.Response;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/** Warp feign {@link Client} with metrics. */
public class ObservedClientInterceptor implements ClientInterceptor {

  private final ObservationRegistry observationRegistry;

  private final FeignObservationConvention customFeignObservationConvention;

  public ObservedClientInterceptor(ObservationRegistry observationRegistry,
      FeignObservationConvention customFeignObservationConvention) {
    this.observationRegistry = observationRegistry;
    this.customFeignObservationConvention = customFeignObservationConvention;
  }

  public ObservedClientInterceptor(ObservationRegistry observationRegistry) {
    this(observationRegistry, null);
  }

  @Override
  public void beforeExecute(ClientInvocationContext clientInvocationContext) {
    FeignContext feignContext = new FeignContext(clientInvocationContext.getRequestTemplate());
    Observation observation = FeignDocumentedObservation.DEFAULT
        .observation(this.customFeignObservationConvention,
            DefaultFeignObservationConvention.INSTANCE, feignContext, this.observationRegistry)
        .start();
    clientInvocationContext.getHolder().put(Observation.class, observation);
    clientInvocationContext.getHolder().put(FeignContext.class, feignContext);
  }

  @Override
  public void afterExecute(ClientInvocationContext clientInvocationContext,
                           Response response,
                           Throwable exception) {
    FeignContext feignContext =
        (FeignContext) clientInvocationContext.getHolder().get(FeignContext.class);
    Observation observation =
        (Observation) clientInvocationContext.getHolder().get(Observation.class);
    if (feignContext == null) {
      return;
    }
    feignContext.setResponse(response);
    if (exception != null) {
      observation.error(exception);
    }
    observation.stop();
  }
}
