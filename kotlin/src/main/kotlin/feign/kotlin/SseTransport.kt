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
package feign.kotlin

import feign.Experimental
import feign.Request
import org.reactivestreams.Publisher

/**
 * Executes the HTTP request of an [Sse]-annotated method and publishes the returned Server-Sent
 * Events incrementally.
 *
 * Implementations own the transport: they must not aggregate the response body before emitting
 * events, and they should stop reading as soon as the returned [Publisher] is cancelled. Event
 * conversion, error signaling and timeouts are the transport's responsibility; events are
 * published in arrival order. Implementations do not gain automatic reconnect or
 * `Last-Event-ID` replay from this SPI; clients must opt into and implement those policies
 * explicitly.
 *
 * [feign.webflux.WebClientSseClient] is the standard implementation, built on Spring WebClient's
 * native SSE codec.
 */
@Experimental
fun interface SseTransport {

    /**
     * Sends [request] and returns the server-sent events as they arrive.
     *
     * @param request the request resolved by Feign's normal pipeline: parameters bound, body
     *   encoded, [feign.RequestInterceptor]s and the target already applied
     * @param options the per-request [Request.Options]; a transport that cannot honor them
     *   (streaming has no meaningful total read timeout) must document what it ignores
     * @param methodKey the Feign config key of the invoked method, for use in error reporting
     * @param errorDecoder the configured Feign decoder for non-2xx responses
     * @return a cold publisher; each subscription sends a new request
     */
    fun stream(
        request: Request,
        options: Request.Options,
        methodKey: String,
        errorDecoder: feign.codec.ErrorDecoder,
    ): Publisher<SseEvent>
}
