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
package feign.webflux;

import static feign.Util.checkNotNull;

import feign.AsyncClient;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import feign.Util;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * {@link AsyncClient} implementation that delegates to Spring's {@link WebClient}.
 *
 * <p>Requests are executed on the {@link WebClient}'s reactive transport (Reactor Netty by default)
 * and the returned {@link CompletableFuture} is completed from the transport's completion thread.
 * No thread is parked waiting for a response, which makes this client a natural fit for {@code
 * CoroutineFeign.builder()} and other non-blocking setups.
 *
 * <p>Response bodies are aggregated in memory before being handed over to Feign. Error statuses are
 * surfaced as regular {@link Response} objects instead of exceptions, so they stay under the
 * control of Feign's {@link feign.codec.ErrorDecoder}.
 *
 * <p>Anything configured on the supplied {@link WebClient} - connector, connection pool, filters
 * and codecs - is reused as is. Of the per request {@link Options}, only the read timeout is
 * applied; the connect timeout and redirect handling belong to the underlying connector and should
 * be configured there, ex. {@code HttpClient.create().responseTimeout(...)} for Reactor Netty.
 */
public class WebClientAsyncClient<C> implements AsyncClient<C> {

  private final WebClient webClient;

  /** Creates a client backed by the default {@link WebClient}. */
  public WebClientAsyncClient() {
    this(WebClient.create());
  }

  /**
   * Creates a client from the supplied builder, so that existing WebClient configuration can be
   * reused.
   *
   * @param builder builder used to create the underlying {@link WebClient}
   */
  public WebClientAsyncClient(WebClient.Builder builder) {
    this(builder.build());
  }

  /**
   * Creates a client backed by the supplied {@link WebClient}.
   *
   * @param webClient the client requests are delegated to
   */
  public WebClientAsyncClient(WebClient webClient) {
    this.webClient = checkNotNull(webClient, "webClient");
  }

  @Override
  public CompletableFuture<Response> execute(
      Request request, Options options, Optional<C> requestContext) {
    final Mono<Response> result;
    try {
      result =
          toWebClientRequest(request)
              .exchangeToMono(response -> response.toEntity(byte[].class))
              .map(entity -> toFeignResponse(entity, request));
    } catch (final RuntimeException e) {
      // requests that cannot even be built, ex. a malformed url, fail the returned future
      // instead of throwing from execute
      final CompletableFuture<Response> failed = new CompletableFuture<>();
      failed.completeExceptionally(e);
      return failed;
    }

    final long readTimeoutMillis = options.readTimeoutMillis();
    if (readTimeoutMillis > 0) {
      return result.timeout(Duration.ofMillis(readTimeoutMillis)).toFuture();
    }
    return result.toFuture();
  }

  private WebClient.RequestHeadersSpec<?> toWebClientRequest(final Request request) {
    final WebClient.RequestBodySpec spec =
        webClient
            .method(HttpMethod.valueOf(request.httpMethod().name()))
            .uri(URI.create(request.url()));

    boolean hasContentType = false;
    for (final Map.Entry<String, Collection<String>> header : request.headers().entrySet()) {
      // the transport computes Content-Length from the body it sends
      if (Util.CONTENT_LENGTH.equalsIgnoreCase(header.getKey())) {
        continue;
      }
      if (Util.CONTENT_TYPE.equalsIgnoreCase(header.getKey())) {
        hasContentType = true;
      }
      spec.header(header.getKey(), header.getValue().toArray(new String[0]));
    }

    final byte[] body = request.body();
    if (body == null) {
      return spec;
    }

    if (!hasContentType) {
      // writing a byte[] body requires a content type to select an HttpMessageWriter
      spec.contentType(MediaType.APPLICATION_OCTET_STREAM);
    }
    return spec.bodyValue(body);
  }

  private Response toFeignResponse(final ResponseEntity<byte[]> entity, final Request request) {
    final int status = entity.getStatusCode().value();
    return Response.builder()
        .status(status)
        .reason(reasonPhrase(status))
        .headers(toFeignHeaders(entity.getHeaders()))
        .body(entity.getBody())
        .request(request)
        .build();
  }

  /**
   * WebClient does not expose the reason phrase sent by the server, so fall back to the standard
   * one.
   */
  private String reasonPhrase(final int status) {
    final HttpStatus httpStatus = HttpStatus.resolve(status);
    return httpStatus != null ? httpStatus.getReasonPhrase() : null;
  }

  private Map<String, Collection<String>> toFeignHeaders(final HttpHeaders headers) {
    final Map<String, Collection<String>> result = new LinkedHashMap<>();
    headers.forEach((name, values) -> result.put(name, new ArrayList<>(values)));
    return result;
  }
}
