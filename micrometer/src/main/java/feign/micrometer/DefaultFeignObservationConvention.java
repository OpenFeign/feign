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

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;

/**
 * Default implementation of {@link FeignObservationConvention}.
 *
 * @see FeignObservationConvention
 * @since 12.1
 */
public class DefaultFeignObservationConvention implements FeignObservationConvention {

  /** Singleton instance of this convention. */
  public static final DefaultFeignObservationConvention INSTANCE =
      new DefaultFeignObservationConvention();

  // There is no need to instantiate this class multiple times, but it may be extended,
  // hence protected visibility.
  protected DefaultFeignObservationConvention() {}

  @Override
  public String getName() {
    return "http.client.requests";
  }

  @Override
  public String getContextualName(FeignContext context) {
    return "HTTP " + getMethodString(context.getCarrier());
  }

  @Override
  public KeyValues getLowCardinalityKeyValues(FeignContext context) {
    RequestTemplate requestTemplate = context.getCarrier().requestTemplate();
    return KeyValues.of(
        FeignObservationDocumentation.HttpClientTags.METHOD.withValue(
            getMethodString(context.getCarrier())),
        FeignObservationDocumentation.HttpClientTags.URI.withValue(
            requestTemplate.methodMetadata().template().url()),
        FeignObservationDocumentation.HttpClientTags.STATUS.withValue(
            getStatusValue(context.getResponse())),
        FeignObservationDocumentation.HttpClientTags.CLIENT_NAME.withValue(
            requestTemplate.feignTarget().type().getName()));
  }

  String getStatusValue(@Nullable Response response) {
    return response != null ? String.valueOf(response.status()) : "CLIENT_ERROR";
  }

  String getMethodString(@Nullable Request request) {
    if (request == null) {
      return "UNKNOWN";
    }
    return request.httpMethod().name();
  }
}
