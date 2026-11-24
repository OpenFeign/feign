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
 * Runs the HTTP request of an [Sse] method and publishes the Server-Sent Events one by one as they
 * arrive.
 *
 * Implementations must not aggregate a 2xx response body before emitting events, and should stop
 * reading as soon as the returned [Publisher] is cancelled. A non-2xx response is signalled as
 * [SseErrorResponseException]; Feign then runs the configured [feign.codec.ErrorDecoder] on it.
 * There is no automatic reconnect or `Last-Event-ID` replay.
 *
 * [feign.webflux.WebClientSseClient] is the standard implementation.
 */
@Experimental
fun interface SseClient {

    /**
     * Returns a cold publisher of the events for [request]; each subscription sends a new request.
     *
     * @param request the fully resolved request: parameters bound, body encoded,
     *   [feign.RequestInterceptor]s and the target applied
     * @param options the per-request [Request.Options]; an implementation that cannot honor them
     *   must document what it ignores
     */
    fun createEventPublisher(request: Request, options: Request.Options): Publisher<SseEvent>
}
