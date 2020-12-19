/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.http2client;

import feign.Request;
import feign.Request.Options;
import feign.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

public abstract class AbstractHttpClient {

  protected Response toFeignResponse(Request request, HttpResponse<byte[]> httpResponse) {
    final OptionalLong length = httpResponse.headers().firstValueAsLong("Content-Length");

    return Response.builder()
        .body(new ByteArrayInputStream(httpResponse.body()),
            length.isPresent() ? (int) length.getAsLong() : null)
        .reason(httpResponse.headers().firstValue("Reason-Phrase").orElse("OK"))
        .request(request)
        .status(httpResponse.statusCode())
        .headers(castMapCollectType(httpResponse.headers().map()))
        .build();
  }

  protected Builder newRequestBuilder(Request request, Options options) throws URISyntaxException {
    URI uri = new URI(request.url());

    final BodyPublisher body;
    final byte[] data = request.body();
    if (data == null) {
      body = BodyPublishers.noBody();
    } else {
      body = BodyPublishers.ofByteArray(data);
    }

    final Builder requestBuilder = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(Duration.ofMillis(options.readTimeoutMillis()));

    final Map<String, Collection<String>> headers = filterRestrictedHeaders(request.headers());
    if (!headers.isEmpty()) {
      requestBuilder.headers(asString(headers));
    }

    switch (request.httpMethod()) {
      case GET:
        return requestBuilder.GET();
      case POST:
        return requestBuilder.POST(body);
      case PUT:
        return requestBuilder.PUT(body);
      case DELETE:
        return requestBuilder.DELETE();
      default:
        // fall back scenario, http implementations may restrict some methods
        return requestBuilder.method(request.httpMethod().toString(), body);
    }
  }

  protected Map<String, Collection<String>> filterRestrictedHeaders(Map<String, Collection<String>> headers) {
    return headers;
  }

    private Map<String, Collection<String>> castMapCollectType(Map<String, List<String>> map) {
    final Map<String, Collection<String>> result = new HashMap<>();
    map.forEach((key, value) -> result.put(key, new HashSet<>(value)));
    return result;
  }

  private String[] asString(Map<String, Collection<String>> headers) {
    return headers.entrySet().stream()
        .flatMap(entry -> entry.getValue()
            .stream()
            .map(value -> Arrays.asList(entry.getKey(), value))
            .flatMap(List::stream))
        .toArray(String[]::new);
  }

}
