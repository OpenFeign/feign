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

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * A {@link DefaultFeignObservationConvention} that additionally emits the target host and, when
 * explicitly present, the target port derived from the Feign {@code Target} url.
 *
 * <p>The host and port are taken from {@code
 * context.getCarrier().requestTemplate().feignTarget().url()} — the configured target url, not the
 * final resolved request url. Only a port that is explicitly present in that url is emitted; no
 * default port (80/443) is inferred and no scheme is added. The port is only emitted alongside a
 * valid host.
 *
 * <p>This convention is strictly opt-in via {@link
 * MicrometerObservationCapability#MicrometerObservationCapability(io.micrometer.observation.ObservationRegistry,
 * FeignObservationConvention)}. The {@link DefaultFeignObservationConvention} and its output remain
 * unchanged.
 *
 * <p><strong>Cardinality warning:</strong> {@code net.peer.host} and {@code net.peer.port} are
 * emitted as <em>low-cardinality</em> key values. This is only appropriate when the set of targets
 * is small and bounded. Enabling this against clients that talk to a large or unbounded set of
 * hosts (for example, per-tenant hostnames) can cause a metric-tag cardinality explosion in your
 * metrics backend. Enable it only when you know the target set is limited.
 *
 * @author Henrique (henriquejsza)
 * @see DefaultFeignObservationConvention
 * @since 13.15
 */
public class TargetHostAndPortFeignObservationConvention extends DefaultFeignObservationConvention {

  /** Singleton instance of this convention. */
  public static final TargetHostAndPortFeignObservationConvention INSTANCE =
      new TargetHostAndPortFeignObservationConvention();

  // There is no need to instantiate this class multiple times, but it may be extended,
  // hence protected visibility.
  protected TargetHostAndPortFeignObservationConvention() {}

  @Override
  public boolean supportsContext(Observation.Context context) {
    return context instanceof FeignContext;
  }

  @Override
  public KeyValues getLowCardinalityKeyValues(FeignContext context) {
    KeyValues keyValues = super.getLowCardinalityKeyValues(context);
    URI targetUri = targetUri(context);
    if (targetUri == null) {
      return keyValues;
    }
    String host = targetUri.getHost();
    if (host == null || host.isEmpty()) {
      return keyValues;
    }
    keyValues =
        keyValues.and(FeignObservationDocumentation.HttpClientTags.TARGET_HOST.withValue(host));
    int port = targetUri.getPort();
    if (port != -1) {
      keyValues =
          keyValues.and(
              FeignObservationDocumentation.HttpClientTags.TARGET_PORT.withValue(
                  String.valueOf(port)));
    }
    return keyValues;
  }

  /**
   * Parses the configured target url defensively. Returns {@code null} when the url is unavailable
   * (for example an {@code EmptyTarget}) or is not a valid {@link URI}, so that a missing or
   * invalid url never breaks the observation.
   */
  private static URI targetUri(FeignContext context) {
    try {
      String url = context.getCarrier().requestTemplate().feignTarget().url();
      if (url == null || url.isEmpty()) {
        return null;
      }
      return new URI(url);
    } catch (URISyntaxException | UnsupportedOperationException ignored) {
      return null;
    }
  }
}
