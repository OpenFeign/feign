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

import feign.FeignException;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import feign.kotlin.SseEvent;
import feign.kotlin.SseTransport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * {@link SseTransport} implementation that streams Server-Sent Events through Spring's {@link
 * WebClient}, so {@code @Sse} methods of a {@code feign-kotlin} {@code CoroutineFeign} client can
 * emit events as they arrive instead of waiting for the whole response body.
 *
 * <p>The request is translated exactly like the buffered {@link WebClientAsyncClient} does (shared
 * header/body semantics), but the 2xx body is decoded incrementally with Spring's standard SSE
 * codec; nothing aggregates the stream. Cancelling the subscription disposes the exchange and
 * closes the connection.
 *
 * <p>Behavior notes:
 *
 * <ul>
 *   <li>Error responses (4xx/5xx) are buffered in full - error bodies are bounded - and surfaced as
 *       a {@link FeignException} carrying the status and body, mirroring how the buffered client
 *       keeps error statuses under Feign's control.
 *   <li>The per-request {@link Options} read timeout is deliberately not applied as an overall
 *       timeout: it would cap the duration of the whole stream instead of protecting against a
 *       stalled one. Connect timeouts, idle timeouts and other stream shaping belong to the
 *       configured connector and WebClient.
 *   <li>Each subscription sends a new request; events already delivered are never replayed and no
 *       retry is attempted for partially consumed streams.
 * </ul>
 */
public class WebClientSseClient implements SseTransport {

  private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_EVENT =
      new ParameterizedTypeReference<ServerSentEvent<String>>() {};

  private final WebClient webClient;

  /** Creates a client backed by the default {@link WebClient}. */
  public WebClientSseClient() {
    this(WebClient.create());
  }

  /**
   * Creates a client from the supplied builder, so that existing WebClient configuration can be
   * reused.
   *
   * @param builder builder used to create the underlying {@link WebClient}
   */
  public WebClientSseClient(WebClient.Builder builder) {
    this(builder.build());
  }

  /**
   * Creates a client backed by the supplied {@link WebClient}.
   *
   * @param webClient the client requests are delegated to
   */
  public WebClientSseClient(WebClient webClient) {
    this.webClient = checkNotNull(webClient, "webClient");
  }

  @Override
  public Publisher<SseEvent> stream(Request request, Options options, String methodKey) {
    final WebClient.RequestHeadersSpec<?> spec;
    try {
      spec = WebClientRequests.create(webClient, request);
    } catch (final RuntimeException e) {
      // requests that cannot even be built, ex. a malformed url, fail on subscription
      // instead of throwing from this method
      return Flux.error(e);
    }
    return spec.exchangeToFlux(response -> streamResponse(response, request, methodKey));
  }

  private Flux<SseEvent> streamResponse(
      final ClientResponse response, final Request request, final String methodKey) {
    if (response.statusCode().isError()) {
      // Error bodies are bounded, so buffering them keeps Feign's ErrorDecoder semantics.
      return response
          .toEntity(byte[].class)
          .flatMapMany(
              entity -> {
                throw FeignException.errorStatus(methodKey, toFeignResponse(entity, request));
              });
    }
    return response.bodyToFlux(SSE_EVENT).map(WebClientSseClient::toSseEvent);
  }

  private static SseEvent toSseEvent(ServerSentEvent<String> event) {
    return new SseEvent(event.data(), event.event(), event.id(), event.retry(), event.comment());
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
