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

import java.util.Iterator;
import feign.Client;
import feign.ClientInterceptor;
import feign.ClientInvocationContext;
import feign.FeignException;
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
  public Response around(ClientInvocationContext context, Iterator<ClientInterceptor> iterator)
      throws FeignException {
    FeignContext feignContext = new FeignContext(context.getRequestTemplate());
    Observation observation = FeignObservationDocumentation.DEFAULT
        .observation(this.customFeignObservationConvention,
            DefaultFeignObservationConvention.INSTANCE, () -> feignContext,
            this.observationRegistry)
        .start();
    Exception ex = null;
    Response response = null;
    try {
      ClientInterceptor interceptor = iterator.next();
      response = interceptor.around(context, iterator);
      return response;
    } catch (FeignException exception) {
      ex = exception;
      throw exception;
    } finally {
      feignContext.setResponse(response);
      if (ex != null) {
        observation.error(ex);
      }
      observation.stop();
    }
  }
}
