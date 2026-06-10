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
package feign.micrometer;

import feign.AsyncClient;
import feign.Capability;
import feign.Client;
import feign.Response;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.CompletionException;

/** Wrap feign {@link Client} with metrics. */
public class MicrometerObservationCapability implements Capability {

  private final ObservationRegistry observationRegistry;

  private final FeignObservationConvention customFeignObservationConvention;

  public MicrometerObservationCapability(
      ObservationRegistry observationRegistry,
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

      Observation observation =
          FeignObservationDocumentation.DEFAULT
              .observation(
                  this.customFeignObservationConvention,
                  DefaultFeignObservationConvention.INSTANCE,
                  () -> feignContext,
                  this.observationRegistry)
              .start();

      try (Observation.Scope scope = observation.openScope()) {
        Response response = client.execute(request, options);
        feignContext.setResponse(response);
        return response;
      } catch (Throwable ex) {
        observation.error(ex);
        throw ex;
      } finally {
        observation.stop();
      }
    };
  }

  @Override
  public AsyncClient<Object> enrich(AsyncClient<Object> client) {
    return (request, options, context) -> {
      FeignContext feignContext = new FeignContext(request);

      Observation observation =
          FeignObservationDocumentation.DEFAULT
              .observation(
                  this.customFeignObservationConvention,
                  DefaultFeignObservationConvention.INSTANCE,
                  () -> feignContext,
                  this.observationRegistry)
              .start();

      try (Observation.Scope scope = observation.openScope()) {
        return client
            .execute(feignContext.getCarrier(), options, context)
            .whenComplete(
                (response, ex) -> {
                  feignContext.setResponse(response);
                  if (ex != null) {
                    observation.error(unwrap(ex));
                  }
                  observation.stop();
                });
      } catch (Throwable ex) {
        observation.error(ex);
        observation.stop();
        throw ex;
      }
    };
  }

  private static Throwable unwrap(Throwable ex) {
    if (ex instanceof CompletionException && ex.getCause() != null) {
      return ex.getCause();
    }
    return ex;
  }
}
