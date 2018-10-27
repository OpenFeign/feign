/**
 * Copyright 2012-2018 The Feign Authors
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import feign.*;
import feign.Request.Options;

public class Http2Client implements Client {

  private final HttpClient client;

  public Http2Client() {
    this(HttpClient.newBuilder()
        .followRedirects(Redirect.ALWAYS)
        .version(Version.HTTP_2)
        .build());
  }

  public Http2Client(HttpClient client) {
    this.client = Util.checkNotNull(client, "http cliet must be not unll");
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final HttpRequest httpRequest = newRequestBuilder(request).build();

    HttpResponse<byte[]> httpResponse;
    try {
      httpResponse = client.send(httpRequest, BodyHandlers.ofByteArray());
    } catch (final InterruptedException e) {
      throw new IOException("Invalid uri " + request.url(), e);
    }

    final OptionalLong length = httpResponse.headers().firstValueAsLong("Content-Length");

    final Response response = Response.builder()
        .body(new ByteArrayInputStream(httpResponse.body()),
            length.isPresent() ? (int) length.getAsLong() : null)
        .reason(httpResponse.headers().firstValue("Reason-Phrase").orElse("OK"))
        .request(request)
        .status(httpResponse.statusCode())
        .headers(castMapCollectType(httpResponse.headers().map()))
        .build();
    return response;
  }

  private Builder newRequestBuilder(Request request) throws IOException {
    URI uri;
    try {
      uri = new URI(request.url());
    } catch (final URISyntaxException e) {
      throw new IOException("Invalid uri " + request.url(), e);
    }

    final BodyPublisher body;
    if (request.body() == null) {
      body = BodyPublishers.noBody();
    } else {
      body = BodyPublishers.ofByteArray(request.body());
    }

    final Builder requestBuilder = HttpRequest.newBuilder()
        .uri(uri)
        .version(Version.HTTP_2);

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

  /**
   * There is a bunch o headers that the http2 client do not allow to be set.
   *
   * @see jdk.internal.net.http.common.Utils.DISALLOWED_HEADERS_SET
   */
  private static final Set<String> DISALLOWED_HEADERS_SET;

  static {
    // A case insensitive TreeSet of strings.
    final TreeSet<String> treeSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    treeSet.addAll(Set.of("connection", "content-length", "date", "expect", "from", "host",
        "origin", "referer", "upgrade", "via", "warning"));
    DISALLOWED_HEADERS_SET = Collections.unmodifiableSet(treeSet);
  }

  private Map<String, Collection<String>> filterRestrictedHeaders(Map<String, Collection<String>> headers) {
    final Map<String, Collection<String>> filteredHeaders = headers.keySet()
        .stream()
        .filter(headerName -> !DISALLOWED_HEADERS_SET.contains(headerName))
        .collect(Collectors.toMap(
            Function.identity(),
            headers::get));

    filteredHeaders.computeIfAbsent("Accept", key -> List.of("*/*"));

    return filteredHeaders;
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
        .collect(Collectors.toList())
        .toArray(new String[0]);
  }

}
