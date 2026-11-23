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

import feign.Experimental;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
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
 * Spring key set. The values follow Spring's rules where Feign exposes the same information:
 *
 * <ul>
 *   <li>{@code method}: the HTTP method name (or {@value KeyValue#NONE_VALUE} without a request);
 *   <li>{@code uri}: the path of the Feign method's request line template, with any protocol, host
 *       and port stripped (or {@value KeyValue#NONE_VALUE} when unavailable) - only the path is
 *       reported, never the full url;
 *   <li>{@code status}: the raw response status code, or {@code CLIENT_ERROR} when no response was
 *       received;
 *   <li>{@code outcome}: {@code SUCCESS} for any 2xx response; the series name ({@code
 *       INFORMATIONAL}, {@code REDIRECTION}, {@code CLIENT_ERROR}, {@code SERVER_ERROR}) for a
 *       status code known to Spring Framework 6.2's {@code HttpStatus}; {@code UNKNOWN} for any
 *       other status code (for example 499 or 599) or when no response was received;
 *   <li>{@code client.name}: the host of the request url (or {@value KeyValue#NONE_VALUE}) - note
 *       that unlike the default convention's {@code clientName} this is not the Feign target type
 *       name;
 *   <li>{@code exception}: the simple name of the exception thrown by the client (or {@value
 *       KeyValue#NONE_VALUE}).
 * </ul>
 *
 * <p>Known differences from Spring's own clients, for the same request:
 *
 * <ul>
 *   <li>{@code exception} is {@value KeyValue#NONE_VALUE} for 4xx/5xx responses. The observation
 *       covers the {@link feign.Client} call only; the {@link feign.FeignException} for an error
 *       status is raised afterwards, by the error decoder, once the observation has stopped. {@code
 *       RestTemplate} and {@code RestClient} run their status handlers inside the observation and
 *       report the resulting exception (for example {@code NotFound}).
 *   <li>{@code uri} is taken from the Feign method template only: a path carried by the target url
 *       is not included, and a method invoked with an absolute {@link java.net.URI} argument
 *       reports {@code /}. Spring reports {@value KeyValue#NONE_VALUE} for requests that were not
 *       built from a URI template.
 *   <li>{@code status} is never {@code IO_ERROR}: Feign always has the status code at hand.
 *   <li>{@code outcome} follows Spring Framework 6.2. Spring Framework 7 removed the deprecated
 *       {@code 305}, {@code 419} and {@code 420} from {@code HttpStatus}, so it reports {@code
 *       UNKNOWN} for those three codes where this convention reports the series name.
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
 * @since 13.19
 */
@Experimental
public class SpringCompatibleFeignObservationConvention extends DefaultFeignObservationConvention {

  private static final Pattern PATTERN_BEFORE_PATH = Pattern.compile("^https?://[^/]+");

  /**
   * The non-2xx status codes declared by Spring Framework 6.2's {@code HttpStatus}; Spring reports
   * {@code UNKNOWN} as the outcome of any other non-2xx code.
   */
  private static final int[] SPRING_HTTP_STATUS_CODES = {
    100, 101, 102, 103, //
    300, 301, 302, 303, 304, 305, 307, 308, //
    400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416, 417, 418,
    419, 420, 421, 422, 423, 424, 425, 426, 428, 429, 431, 451, //
    500, 501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511
  };

  /** Singleton instance of this convention. */
  public static final SpringCompatibleFeignObservationConvention INSTANCE =
      new SpringCompatibleFeignObservationConvention();

  /** Use {@link #INSTANCE}; the constructor is only visible so the convention can be extended. */
  protected SpringCompatibleFeignObservationConvention() {}

  @Override
  public boolean supportsContext(Observation.Context context) {
    return context instanceof FeignContext;
  }

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
    Request request = context.getCarrier();
    return FeignObservationDocumentation.SpringHttpClientTags.METHOD.withValue(
        request != null ? request.httpMethod().name() : KeyValue.NONE_VALUE);
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
    Request request = context.getCarrier();
    RequestTemplate requestTemplate = request != null ? request.requestTemplate() : null;
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

  /** Mirrors Spring's {@code DefaultClientRequestObservationConvention.HttpOutcome}. */
  private static String outcomeForStatus(int statusCode) {
    if (statusCode / 100 == 2) {
      return "SUCCESS";
    }
    if (Arrays.binarySearch(SPRING_HTTP_STATUS_CODES, statusCode) < 0) {
      return "UNKNOWN";
    }
    switch (statusCode / 100) {
      case 1:
        return "INFORMATIONAL";
      case 3:
        return "REDIRECTION";
      case 4:
        return "CLIENT_ERROR";
      default:
        return "SERVER_ERROR";
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
