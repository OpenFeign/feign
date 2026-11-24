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

import com.google.gson.Gson
import feign.Capability
import feign.FeignException
import feign.Headers
import feign.Param
import feign.Request
import feign.RequestInterceptor
import feign.RequestLine
import feign.Response
import feign.Util
import feign.codec.Decoder
import feign.codec.DefaultDecoder
import feign.codec.ErrorDecoder
import feign.interceptor.MethodInterceptor
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

/** Routing and validation of [Sse] methods in [CoroutineFeign], against test [SseClient]s. */
class CoroutineSseTest {

    interface StreamApi {
        @RequestLine("GET /events/{id}")
        @Headers("Accept: text/event-stream")
        @Sse
        suspend fun streamEvents(@Param("id") id: String): Flow<SseEvent>

        @RequestLine("POST /chat/{room}")
        @Headers("Content-Type: application/json")
        @Sse
        fun streamChatReplies(@Param("room") room: String, body: ChatRequest): Flow<SseEvent>

        @RequestLine("GET /options")
        @Sse
        fun streamEventsWithOptions(options: Request.Options): Flow<SseEvent>

        @RequestLine("GET /buffered")
        suspend fun getBufferedBody(): String
    }

    interface FlowOfStringApi {
        @RequestLine("GET /wrong-element-type")
        @Sse
        fun streamStrings(): Flow<String>
    }

    interface NonFlowSuspendApi {
        @RequestLine("GET /not-a-flow")
        @Sse
        suspend fun streamWithoutFlow(): String
    }

    data class ChatRequest(val model: String)

    private class RecordingSseClient : SseClient {
        val requests = CopyOnWriteArrayList<Request>()
        val requestOptions = CopyOnWriteArrayList<Request.Options>()

        override fun createEventPublisher(
            request: Request,
            options: Request.Options
        ): Publisher<SseEvent> {
            requests.add(request)
            requestOptions.add(options)
            return listOf(SseEvent("first"), SseEvent("second")).asFlow().asFlux()
        }
    }

    private class HangingSseClient : SseClient {
        val cancellations = AtomicInteger()

        override fun createEventPublisher(
            request: Request,
            options: Request.Options
        ): Publisher<SseEvent> =
            flow<SseEvent> {
                try {
                    emit(SseEvent("first"))
                    awaitCancellation()
                } finally {
                    cancellations.incrementAndGet()
                }
            }
                .asFlux()
    }

    private fun newGsonCoroutineBuilder(sseClient: SseClient?): CoroutineFeign.CoroutineBuilder<Any?> {
        @Suppress("UNCHECKED_CAST")
        val coroutineBuilder =
            CoroutineFeign.builder<Any?>()
                .decoder(DefaultDecoder() as Decoder)
                .encoder { `object`, _, template ->
                    val requestBody =
                        if (`object` is ChatRequest) Gson().toJson(`object`) else `object`.toString()
                    template.body(requestBody)
                }
        sseClient?.let { coroutineBuilder.sseClient(it) }
        return coroutineBuilder
    }

    @Test
    fun `suspend flow method streams client events with encoded path and headers`(): Unit =
        runBlocking {
            val sseClient = RecordingSseClient()
            val api =
                newGsonCoroutineBuilder(sseClient).target(StreamApi::class.java, "http://localhost:1234")

            val events = api.streamEvents("a b").toList()

            assertThat(events).containsExactly(SseEvent("first"), SseEvent("second"))
            val request = sseClient.requests.single()
            assertThat(request.url()).isEqualTo("http://localhost:1234/events/a%20b")
            assertThat(request.headers()["Accept"]).containsExactly("text/event-stream")
        }

    @Test
    fun `capability can enrich the configured SSE client`() = runBlocking {
        val enrichments = AtomicInteger()
        val capability =
            object : Capability {
                fun enrich(sseClient: SseClient): SseClient =
                    SseClient { request, options ->
                        enrichments.incrementAndGet()
                        sseClient.createEventPublisher(request, options)
                    }
            }
        val api =
            newGsonCoroutineBuilder(RecordingSseClient())
                .addCapability(capability)
                .target(StreamApi::class.java, "http://localhost:1234")

        assertThat(api.streamEvents("capability").toList()).hasSize(2)
        assertThat(enrichments.get()).isEqualTo(1)
    }

    @Test
    fun `non suspend flow method encodes the body and applies interceptors per call`(): Unit =
        runBlocking {
            val sseClient = RecordingSseClient()
            val interceptorInvocations = AtomicInteger()
            val api =
                newGsonCoroutineBuilder(sseClient)
                    .requestInterceptor(
                        RequestInterceptor {
                            it.header("X-Token", "t-${interceptorInvocations.incrementAndGet()}")
                        })
                    .target(StreamApi::class.java, "http://localhost:1234/")

            val firstCallEvents = api.streamChatReplies("room 1", ChatRequest("gpt")).toList()
            val secondCallEvents = api.streamChatReplies("room 1", ChatRequest("gpt")).toList()

            assertThat(firstCallEvents).hasSize(2)
            assertThat(secondCallEvents).hasSize(2)
            assertThat(sseClient.requests).hasSize(2)
            assertThat(interceptorInvocations.get()).isEqualTo(2)
            val body = String(sseClient.requests.first().body(), Util.UTF_8)
            assertThat(body).isEqualTo(Gson().toJson(ChatRequest("gpt")))
            assertThat(sseClient.requests.first().url())
                .isEqualTo("http://localhost:1234/chat/room%201")
            assertThat(sseClient.requests.first().headers()["X-Token"]).containsExactly("t-1")
            assertThat(sseClient.requests.last().headers()["X-Token"]).containsExactly("t-2")
        }

    @Test
    fun `collecting the same flow twice sends two requests and reruns interceptors`(): Unit =
        runBlocking {
            val sseClient = RecordingSseClient()
            val interceptorInvocations = AtomicInteger()
            val api =
                newGsonCoroutineBuilder(sseClient)
                    .requestInterceptor(
                        RequestInterceptor {
                            it.header("X-Token", "t-${interceptorInvocations.incrementAndGet()}")
                        })
                    .target(StreamApi::class.java, "http://localhost:1234/")
            val events = api.streamChatReplies("room", ChatRequest("gpt"))

            events.toList()
            events.toList()

            assertThat(sseClient.requests).hasSize(2)
            assertThat(sseClient.requests.first().headers()["X-Token"]).containsExactly("t-1")
            assertThat(sseClient.requests.last().headers()["X-Token"]).containsExactly("t-2")
        }

    @Test
    fun `error responses go through the configured error decoder with the config key`(): Unit =
        runBlocking {
            val decodedKeys = CopyOnWriteArrayList<String>()
            val errorResponse =
                Response.builder()
                    .status(503)
                    .reason("Service Unavailable")
                    .request(
                        Request.create(
                            Request.HttpMethod.GET, "http://localhost/events/x", emptyMap(), null, Util.UTF_8, null))
                    .body("busy", Util.UTF_8)
                    .build()
            val api =
                newGsonCoroutineBuilder { _, _ -> Flux.error(SseErrorResponseException(errorResponse)) }
                    .errorDecoder(
                        ErrorDecoder { methodKey, response ->
                            decodedKeys.add(methodKey)
                            IllegalStateException("decoded ${response.status()}")
                        })
                    .target(StreamApi::class.java, "http://localhost:1234")

            assertThatThrownBy { runBlocking { api.streamEvents("x").toList() } }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("decoded 503")
            assertThat(decodedKeys.single()).startsWith("StreamApi#streamEvents")
        }

    @Test
    fun `error responses fall back to a feign exception when the decoder returns null`(): Unit =
        runBlocking {
            val errorResponse =
                Response.builder()
                    .status(500)
                    .request(
                        Request.create(
                            Request.HttpMethod.GET, "http://localhost/events/x", emptyMap(), null, Util.UTF_8, null))
                    .build()
            val api =
                newGsonCoroutineBuilder { _, _ -> Flux.error(SseErrorResponseException(errorResponse)) }
                    .errorDecoder { _, _ -> null }
                    .target(StreamApi::class.java, "http://localhost:1234")

            assertThatThrownBy { runBlocking { api.streamEvents("x").toList() } }
                .isInstanceOf(FeignException.InternalServerError::class.java)
        }

    @Test
    fun `method interceptors registered on the builder run`(): Unit = runBlocking {
        val interceptedMethods = CopyOnWriteArrayList<String>()
        val api =
            newGsonCoroutineBuilder(RecordingSseClient())
                .methodInterceptor(
                    MethodInterceptor { invocation, chain ->
                        interceptedMethods.add(invocation.method().name)
                        chain.next(invocation)
                    })
                .target(StreamApi::class.java, "http://localhost:1234")

        api.streamEvents("x").toList()

        assertThat(interceptedMethods).containsExactly("streamEvents")
    }

    @Test
    fun `options argument overrides the builder options on the streaming path`(): Unit = runBlocking {
        val sseClient = RecordingSseClient()
        val api =
            newGsonCoroutineBuilder(sseClient)
                .options(Request.Options(1_000, 2_000))
                .target(StreamApi::class.java, "http://localhost:1234")

        api.streamEventsWithOptions(Request.Options(3_000, 4_000)).toList()

        assertThat(sseClient.requestOptions.single().connectTimeoutMillis()).isEqualTo(3_000)
        assertThat(sseClient.requestOptions.single().readTimeoutMillis()).isEqualTo(4_000)
    }

    @Test
    fun `builder options are used when the method has no options argument`(): Unit = runBlocking {
        val sseClient = RecordingSseClient()
        val api =
            newGsonCoroutineBuilder(sseClient)
                .options(Request.Options(1_000, 2_000))
                .target(StreamApi::class.java, "http://localhost:1234")

        api.streamEvents("x").toList()

        assertThat(sseClient.requestOptions.single().connectTimeoutMillis()).isEqualTo(1_000)
    }

    @Test
    fun `cancelling collection cancels the client subscription`(): Unit = runTest {
        val sseClient = HangingSseClient()
        val api = newGsonCoroutineBuilder(sseClient).target(StreamApi::class.java, "http://localhost:1234")

        val job = launch { api.streamEvents("cancel").collect {} }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        assertThat(sseClient.cancellations.get()).isEqualTo(1)
    }

    @Test
    fun `flow element type other than SseEvent is rejected at target creation`() {
        assertThatThrownBy {
            newGsonCoroutineBuilder(RecordingSseClient())
                .target(FlowOfStringApi::class.java, "http://localhost:1234")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Flow<SseEvent>")
    }

    @Test
    fun `suspend method that does not return a flow is rejected at target creation`() {
        assertThatThrownBy {
            newGsonCoroutineBuilder(RecordingSseClient())
                .target(NonFlowSuspendApi::class.java, "http://localhost:1234")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Flow<SseEvent>")
    }

    @Test
    fun `java default method annotated with sse is rejected at target creation`() {
        assertThatThrownBy {
            newGsonCoroutineBuilder(RecordingSseClient())
                .target(SseDefaultMethodApi::class.java, "http://localhost:1234")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("default method")
    }

    @Test
    fun `sse methods without a configured client are rejected at target creation pointing to sseClient`() {
        assertThatThrownBy {
            newGsonCoroutineBuilder(null).target(StreamApi::class.java, "http://localhost:1234")
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("no SSE client is configured")
            .hasMessageContaining("sseClient")
    }

    @Test
    fun `non sse suspend method on an sse interface makes a regular http call instead of using the sse client`(): Unit =
        runBlocking {
            val server = MockWebServer()
            server.start()
            server.enqueue(MockResponse().setBody("\"hello\""))
            try {
                val sseClient = RecordingSseClient()
                val api =
                    newGsonCoroutineBuilder(sseClient)
                        .target(StreamApi::class.java, server.url("/").toString())

                val responseBody: String = api.getBufferedBody()

                assertThat(responseBody).isEqualTo("\"hello\"")
                assertThat(server.requestCount).isEqualTo(1)
                assertThat(sseClient.requests).isEmpty()
            } finally {
                server.shutdown()
            }
        }
}
