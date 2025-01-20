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
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.Util;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;

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

      try {
        Response response = client.execute(request, options);
        FeignException exception = null;

        exception = getFeignExceptionOnErrorStatusCode(response, request);

        finalizeObservation(feignContext, observation, exception, response);
        return response;
      } catch (Exception ex) {
        finalizeObservation(feignContext, observation, ex, null);
        throw ex;
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

      try {
        return client
            .execute(feignContext.getCarrier(), options, context)
            .whenComplete(
                (response, ex) -> {
                  FeignException exception = null;

                  exception = getFeignExceptionOnErrorStatusCode(response, request);

                  finalizeObservation(feignContext, observation, exception, response);
                });
      } catch (Exception ex) {
        finalizeObservation(feignContext, observation, ex, null);

        throw ex;
      }
    };
  }

  private void finalizeObservation(
      FeignContext feignContext, Observation observation, Throwable ex, Response response) {
    feignContext.setResponse(response);
    if (ex != null) {
      observation.error(ex);
    }
    observation.stop();
  }

  private boolean isErrorStatusCode(int statusCode) {
    return statusCode >= 400 && statusCode < 600;
  }

  private FeignException getFeignExceptionOnErrorStatusCode(Response response, Request request) {
    try {
      if (isErrorStatusCode(response.status())) {

        if (response.body() != null) {
          response =
              response.toBuilder().body(Util.toByteArray(response.body().asInputStream())).build();
        }

        return FeignException.errorStatus(request.requestTemplate().method(), response);
      }
    } catch (IOException ignored) { // NOPMD
    }

    return null;
  }
}
