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
package feign;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ConfidentialLogger extends Logger {
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final Pattern ACCESS_TOKEN_PATTERN =
      Pattern.compile("^Bearer [A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$");

  private final Logger delegate;

  public ConfidentialLogger(final Logger delegate) {
    this.delegate = delegate;
  }

  @Override
  protected void logRequest(final String configKey, final Level logLevel, final Request request) {
    final Map<String, Collection<String>> filteredHeaders = filterHeaders(request.headers());

    final Request copyRequest =
        Request.create(
            request.httpMethod(),
            request.url(),
            filteredHeaders,
            request.body(),
            request.charset(),
            request.requestTemplate());

    super.logRequest(configKey, logLevel, copyRequest);
  }

  @Override
  protected void log(final String configKey, final String format, final Object... args) {
    delegate.log(configKey, format, args);
  }

  static Map<String, Collection<String>> filterHeaders(
      final Map<String, Collection<String>> headers) {
    final Map<String, Collection<String>> filteredHeaders = new HashMap<>(headers);
    filteredHeaders.computeIfPresent(
        AUTHORIZATION_HEADER,
        (ignored, values) ->
            values.stream()
                .map(
                    value ->
                        ACCESS_TOKEN_PATTERN.matcher(value).matches()
                            ? "Bearer <access token>"
                            : value)
                .collect(Collectors.toList()));
    return filteredHeaders;
  }
}
