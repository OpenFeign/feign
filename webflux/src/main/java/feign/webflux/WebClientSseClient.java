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

import feign.Experimental;
import feign.Request;
import feign.Request.Options;
import feign.kotlin.SseClient;
import feign.kotlin.SseErrorResponseException;
import feign.kotlin.SseEvent;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * {@link SseClient} that streams Server-Sent Events through Spring's {@link WebClient}, so
 * {@code @Sse} methods of a {@code feign-kotlin} {@code CoroutineFeign} client emit events as they
 * arrive.
 *
 * <p>Requests are translated like in {@link WebClientAsyncClient}. A 2xx body is decoded one event
 * at a time with Spring's SSE codec. Cancelling the subscription closes the connection. Feign
 * {@link Options} timeouts are not applied; see the module README.
 */
@Experimental
public class WebClientSseClient implements SseClient {

  private static final ParameterizedTypeReference<ServerSentEvent<String>> STRING_SSE_TYPE =
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
  public Publisher<SseEvent> createEventPublisher(Request request, Options options) {
    final WebClient.RequestHeadersSpec<?> requestSpec;
    try {
      requestSpec = WebClientExchanges.fromFeignRequest(webClient, request);
    } catch (final RuntimeException e) {
      return Flux.error(e);
    }
    return requestSpec.exchangeToFlux(response -> toSseEventsOrErrorResponse(response, request));
  }

  private static Flux<SseEvent> toSseEventsOrErrorResponse(
      final ClientResponse response, final Request request) {
    if (!response.statusCode().is2xxSuccessful()) {
      return response
          .toEntity(byte[].class)
          .flatMapMany(
              entity ->
                  Flux.error(
                      new SseErrorResponseException(
                          WebClientExchanges.toFeignResponse(entity, request))));
    }
    return response.bodyToFlux(STRING_SSE_TYPE).map(WebClientSseClient::toSseEvent);
  }

  private static SseEvent toSseEvent(ServerSentEvent<String> event) {
    return new SseEvent(event.data(), event.event(), event.id(), event.retry(), event.comment());
  }
}
