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
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * A {@link DefaultFeignObservationConvention} that emits the exact low-cardinality key names used
 * by Spring Framework for its own client observations, so that Feign observations of {@code
 * http.client.requests} can be aggregated together with the observations of {@code RestTemplate}
 * and {@code WebClient} (see <a href="https://github.com/OpenFeign/feign/issues/2508">#2508</a>).
 *
 * <p>Micrometer's Prometheus registry requires all meters with the same name to share the same set
 * of tag keys. The default {@link DefaultFeignObservationConvention} emits {@code http.method},
 * {@code http.url}, {@code http.status_code} and {@code clientName}, while Spring Framework (6.1+,
 * {@code DefaultClientRequestObservationConvention}) emits {@code method}, {@code uri}, {@code
 * status}, {@code client.name}, {@code exception} and {@code outcome}. When both kinds of clients
 * observe {@code http.client.requests} in the same application, whichever registers the meter first
 * wins and the other client's observations are silently dropped. This convention emits exactly the
 * Spring key set, with values derived the same way Spring derives them:
 *
 * <ul>
 *   <li>{@code method}: the HTTP method name;
 *   <li>{@code uri}: the path of the request template url (or {@value KeyValue#NONE_VALUE} when
 *       unavailable), like Spring's URI template handling - only the path is reported, never the
 *       full url;
 *   <li>{@code status}: the raw response status code, or {@code CLIENT_ERROR} when no response was
 *       received;
 *   <li>{@code outcome}: {@code SUCCESS} for a 2xx response, otherwise the HTTP status series name
 *       (for example {@code CLIENT_ERROR} or {@code SERVER_ERROR}), or {@code UNKNOWN} when no
 *       response was received;
 *   <li>{@code client.name}: the host of the request url (or {@value KeyValue#NONE_VALUE}) - note
 *       that unlike the default convention's {@code clientName} this is not the Feign target type
 *       name;
 *   <li>{@code exception}: the simple name of the thrown exception (or {@value
 *       KeyValue#NONE_VALUE}).
 * </ul>
 *
 * <p>Only the metric key names and values are aligned; the observation name and the contextual name
 * remain the ones inherited from {@link DefaultFeignObservationConvention}.
 *
 * <p>This convention is strictly opt-in via {@link
 * MicrometerObservationCapability#MicrometerObservationCapability(io.micrometer.observation.ObservationRegistry,
 * FeignObservationConvention)}. The {@link DefaultFeignObservationConvention} and its output remain
 * unchanged, so existing dashboards built on the Feign key names are not affected.
 *
 * @author Inf1mum
 * @see DefaultFeignObservationConvention
 * @see FeignObservationDocumentation.SpringHttpClientTags
 * @since 13.19
 */
public class SpringCompatibleFeignObservationConvention extends DefaultFeignObservationConvention {

  private static final Pattern PATTERN_BEFORE_PATH = Pattern.compile("^https?://[^/]+");

  /** Singleton instance of this convention. */
  public static final SpringCompatibleFeignObservationConvention INSTANCE =
      new SpringCompatibleFeignObservationConvention();

  // There is no need to instantiate this class multiple times, but it may be extended,
  // hence protected visibility.
  protected SpringCompatibleFeignObservationConvention() {}

  @Override
  public KeyValues getLowCardinalityKeyValues(FeignContext context) {
    // Make sure that KeyValues entries are already sorted by name for better performance,
    // mirroring the order used by Spring's DefaultClientRequestObservationConvention.
    return KeyValues.of(
        clientName(context),
        exception(context),
        method(context),
        outcome(context),
        status(context),
        uri(context));
  }

  private KeyValue clientName(FeignContext context) {
    URI requestUri = requestUri(context.getCarrier());
    if (requestUri != null && requestUri.getHost() != null) {
      return FeignObservationDocumentation.SpringHttpClientTags.CLIENT_NAME.withValue(
          requestUri.getHost());
    }
    return FeignObservationDocumentation.SpringHttpClientTags.CLIENT_NAME.withValue(
        KeyValue.NONE_VALUE);
  }

  private KeyValue exception(FeignContext context) {
    Throwable error = context.getError();
    if (error != null) {
      String simpleName = error.getClass().getSimpleName();
      return FeignObservationDocumentation.SpringHttpClientTags.EXCEPTION.withValue(
          simpleName.isEmpty() ? error.getClass().getName() : simpleName);
    }
    return FeignObservationDocumentation.SpringHttpClientTags.EXCEPTION.withValue(
        KeyValue.NONE_VALUE);
  }

  private KeyValue method(FeignContext context) {
    return FeignObservationDocumentation.SpringHttpClientTags.METHOD.withValue(
        getMethodString(context.getCarrier()));
  }

  private KeyValue outcome(FeignContext context) {
    Response response = context.getResponse();
    if (response == null) {
      return FeignObservationDocumentation.SpringHttpClientTags.OUTCOME.withValue("UNKNOWN");
    }
    return FeignObservationDocumentation.SpringHttpClientTags.OUTCOME.withValue(
        outcomeForStatus(response.status()));
  }

  private KeyValue status(FeignContext context) {
    Response response = context.getResponse();
    if (response == null) {
      // Spring reports CLIENT_ERROR as the status of an exchange that never received a response.
      return FeignObservationDocumentation.SpringHttpClientTags.STATUS.withValue("CLIENT_ERROR");
    }
    return FeignObservationDocumentation.SpringHttpClientTags.STATUS.withValue(
        String.valueOf(response.status()));
  }

  private KeyValue uri(FeignContext context) {
    RequestTemplate requestTemplate = context.getCarrier().requestTemplate();
    String url =
        requestTemplate != null && requestTemplate.methodMetadata() != null
            ? requestTemplate.methodMetadata().template().url()
            : null;
    if (url == null || url.isEmpty()) {
      return FeignObservationDocumentation.SpringHttpClientTags.URI.withValue(KeyValue.NONE_VALUE);
    }
    return FeignObservationDocumentation.SpringHttpClientTags.URI.withValue(extractPath(url));
  }

  /**
   * Strips the protocol, host and port part of a template url and makes sure the path starts with a
   * slash, exactly like Spring does for the {@code uri} key value.
   */
  private static String extractPath(String uriTemplate) {
    String path = PATTERN_BEFORE_PATH.matcher(uriTemplate).replaceFirst("");
    return (path.startsWith("/") ? path : "/" + path);
  }

  private static String outcomeForStatus(int statusCode) {
    if (statusCode >= 200 && statusCode < 300) {
      return "SUCCESS";
    }
    switch (statusCode / 100) {
      case 1:
        return "INFORMATIONAL";
      case 3:
        return "REDIRECTION";
      case 4:
        return "CLIENT_ERROR";
      case 5:
        return "SERVER_ERROR";
      default:
        return "UNKNOWN";
    }
  }

  /** Parses the request url defensively, so that an invalid url never breaks the observation. */
  @Nullable
  private static URI requestUri(@Nullable Request request) {
    if (request == null) {
      return null;
    }
    try {
      return new URI(request.url());
    } catch (URISyntaxException | IllegalArgumentException | NullPointerException ignored) {
      return null;
    }
  }
}
