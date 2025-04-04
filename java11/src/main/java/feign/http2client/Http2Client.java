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
package feign.http2client;

import static feign.Util.*;

import feign.AsyncClient;
import feign.Client;
import feign.Request;
import feign.Request.Options;
import feign.Request.ProtocolVersion;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
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
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class Http2Client implements Client, AsyncClient<Object> {

  private final HttpClient client;

  private final Map<Integer, SoftReference<HttpClient>> clients = new ConcurrentHashMap<>();

  /**
   * Creates the new Http2Client using following defaults:
   *
   * <ul>
   *   <li>Connect Timeout: 10 seconds, as {@link Request.Options#Options()} uses
   *   <li>Follow all 3xx redirects
   *   <li>HTTP 2
   * </ul>
   *
   * @see Request.Options#Options()
   */
  public Http2Client() {
    this(
        HttpClient.newBuilder()
            .followRedirects(Redirect.ALWAYS)
            .version(Version.HTTP_2)
            .connectTimeout(Duration.ofMillis(10000))
            .build());
  }

  public Http2Client(Options options) {
    this(newClientBuilder(options).version(Version.HTTP_2).build());
  }

  public Http2Client(HttpClient client) {
    this.client = Util.checkNotNull(client, "HttpClient must not be null");
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final HttpRequest httpRequest;
    try {
      httpRequest = newRequestBuilder(request, options).version(client.version()).build();
    } catch (URISyntaxException e) {
      throw new IOException("Invalid uri " + request.url(), e);
    }

    HttpClient clientForRequest = getOrCreateClient(options);
    HttpResponse<InputStream> httpResponse;
    try {
      httpResponse = clientForRequest.send(httpRequest, BodyHandlers.ofInputStream());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }

    return toFeignResponse(request, httpResponse);
  }

  @Override
  public CompletableFuture<Response> execute(
      Request request, Options options, Optional<Object> requestContext) {
    HttpRequest httpRequest;
    try {
      httpRequest = newRequestBuilder(request, options).build();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid uri " + request.url(), e);
    }

    HttpClient clientForRequest = getOrCreateClient(options);
    CompletableFuture<HttpResponse<InputStream>> future =
        clientForRequest.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
    return future.thenApply(httpResponse -> toFeignResponse(request, httpResponse));
  }

  protected Response toFeignResponse(Request request, HttpResponse<InputStream> httpResponse) {
    final OptionalLong length = httpResponse.headers().firstValueAsLong("Content-Length");

    InputStream body = httpResponse.body();

    if (httpResponse.headers().allValues(CONTENT_ENCODING).contains(ENCODING_GZIP)) {
      try {
        body = new GZIPInputStream(body);
      } catch (IOException ignored) {
      }
    } else if (httpResponse.headers().allValues(CONTENT_ENCODING).contains(ENCODING_DEFLATE)) {
      body = new InflaterInputStream(body);
    }

    return Response.builder()
        .protocolVersion(enumForName(ProtocolVersion.class, httpResponse.version()))
        .body(body, length.isPresent() ? (int) length.getAsLong() : null)
        .reason(httpResponse.headers().firstValue("Reason-Phrase").orElse(null))
        .request(request)
        .status(httpResponse.statusCode())
        .headers(castMapCollectType(httpResponse.headers().map()))
        .build();
  }

  private HttpClient getOrCreateClient(Options options) {
    if (doesClientConfigurationDiffer(options)) {
      // create a new client from the existing one - but with connectTimeout and followRedirect
      // settings from options
      final int clientKey = createClientKey(options);

      SoftReference<HttpClient> requestScopedSoftReference = clients.get(clientKey);
      HttpClient requestScoped =
          requestScopedSoftReference == null ? null : requestScopedSoftReference.get();

      if (requestScoped == null) {
        java.net.http.HttpClient.Builder builder =
            newClientBuilder(options)
                .sslContext(client.sslContext())
                .sslParameters(client.sslParameters())
                .version(client.version());
        client.authenticator().ifPresent(builder::authenticator);
        client.cookieHandler().ifPresent(builder::cookieHandler);
        client.executor().ifPresent(builder::executor);
        client.proxy().ifPresent(builder::proxy);
        requestScoped = builder.build();
        clients.put(clientKey, new SoftReference<>(requestScoped));
      }
      return requestScoped;
    }
    return client;
  }

  private boolean doesClientConfigurationDiffer(Options options) {
    if ((client.followRedirects() == Redirect.ALWAYS) != options.isFollowRedirects()) {
      return true;
    }
    return client
        .connectTimeout()
        .map(timeout -> timeout.toMillis() != options.connectTimeoutMillis())
        .orElse(true);
  }

  /**
   * Creates integer key that represents {@link Options} settings based on {@link
   * Http2Client#doesClientConfigurationDiffer(Options)} method
   *
   * @param options value
   * @return integer key
   */
  public int createClientKey(feign.Request.Options options) {
    int key = options.connectTimeoutMillis();
    if (options.isFollowRedirects()) {
      key |= 1 << 31; // connectTimeoutMillis always positive, so we can use first sign bit for
      // isFollowRedirects flag
    }
    return key;
  }

  private static java.net.http.HttpClient.Builder newClientBuilder(Options options) {
    return HttpClient.newBuilder()
        .followRedirects(options.isFollowRedirects() ? Redirect.ALWAYS : Redirect.NEVER)
        .connectTimeout(Duration.ofMillis(options.connectTimeoutMillis()));
  }

  private Builder newRequestBuilder(Request request, Options options) throws URISyntaxException {
    URI uri = new URI(request.url());

    final BodyPublisher body;
    final byte[] data = request.body();
    if (data == null) {
      body = BodyPublishers.noBody();
    } else {
      body = BodyPublishers.ofByteArray(data);
    }

    final Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(options.readTimeoutMillis()))
            .version(client.version());

    final Map<String, Collection<String>> headers = filterRestrictedHeaders(request.headers());
    if (!headers.isEmpty()) {
      requestBuilder.headers(asString(headers));
    }

    return requestBuilder.method(request.httpMethod().toString(), body);
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
    treeSet.addAll(Set.of("connection", "content-length", "expect", "host", "upgrade"));
    DISALLOWED_HEADERS_SET = Collections.unmodifiableSet(treeSet);
  }

  private Map<String, Collection<String>> filterRestrictedHeaders(
      Map<String, Collection<String>> headers) {
    final Map<String, Collection<String>> filteredHeaders =
        headers.keySet().stream()
            .filter(headerName -> !DISALLOWED_HEADERS_SET.contains(headerName))
            .collect(Collectors.toMap(Function.identity(), headers::get));

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
        .flatMap(
            entry ->
                entry.getValue().stream()
                    .map(value -> Arrays.asList(entry.getKey(), value))
                    .flatMap(List::stream))
        .toArray(String[]::new);
  }
}
