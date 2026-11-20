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
import feign.Headers
import feign.Param
import feign.Request
import feign.RequestInterceptor
import feign.RequestLine
import feign.Util
import feign.codec.Decoder
import feign.codec.DefaultDecoder
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

/**
 * Routing and validation of [Sse] methods in [CoroutineFeign], exercised against stub
 * [SseTransport]s so no HTTP stack is involved on the streaming path.
 */
class CoroutineSseTest {

    interface StreamApi {
        @RequestLine("GET /events/{id}")
        @Headers("Accept: text/event-stream")
        @Sse
        suspend fun events(@Param("id") id: String): Flow<SseEvent>

        @RequestLine("POST /chat/{room}")
        @Headers("Content-Type: application/json")
        @Sse
        fun chat(@Param("room") room: String, body: ChatRequest): Flow<SseEvent>

        @RequestLine("GET /options")
        @Sse
        fun withOptions(options: Request.Options): Flow<SseEvent>

        @RequestLine("GET /buffered")
        suspend fun buffered(): String
    }

    interface FlowOfStringApi {
        @RequestLine("GET /wrong-element-type")
        @Sse
        fun events(): Flow<String>
    }

    interface NonFlowSuspendApi {
        @RequestLine("GET /not-a-flow")
        @Sse
        suspend fun events(): String
    }

    data class ChatRequest(val model: String)

    /** Records requests and replays canned events without any network I/O. */
    private class StubTransport : SseTransport {
        val requests = CopyOnWriteArrayList<Request>()
        val optionsSeen = CopyOnWriteArrayList<Request.Options>()
        val methodKeys = CopyOnWriteArrayList<String>()

        override fun stream(
            request: Request,
            options: Request.Options,
            methodKey: String
        ): Publisher<SseEvent> {
            requests.add(request)
            optionsSeen.add(options)
            methodKeys.add(methodKey)
            return listOf(SseEvent("first"), SseEvent("second")).asFlow().asFlux()
        }
    }

    /** Emits one event and then hangs until cancelled, recording the cancellation. */
    private class HangingTransport : SseTransport {
        val cancellations = AtomicInteger()

        override fun stream(
            request: Request,
            options: Request.Options,
            methodKey: String
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

    private fun builder(transport: SseTransport?): CoroutineFeign.CoroutineBuilder<Any?> {
        @Suppress("UNCHECKED_CAST")
        val delegate =
            CoroutineFeign.builder<Any?>()
                .decoder(DefaultDecoder() as Decoder)
                .encoder { `object`, _, template ->
                    val json =
                        if (`object` is ChatRequest) Gson().toJson(`object`) else `object`.toString()
                    template.body(json)
                }
        transport?.let { delegate.sseTransport(it) }
        return delegate
    }

    @Test
    fun `suspend flow method streams through the transport with native request binding`(): Unit =
        runBlocking {
            val transport = StubTransport()
            val api = builder(transport).target(StreamApi::class.java, "http://localhost:1234")

            val events = api.events("a b").toList()

            assertThat(events).containsExactly(SseEvent("first"), SseEvent("second"))
            val request = transport.requests.single()
            assertThat(request.url()).isEqualTo("http://localhost:1234/events/a%20b")
            assertThat(request.headers()["Accept"]).containsExactly("text/event-stream")
            assertThat(transport.methodKeys.single()).startsWith("StreamApi#events")
        }

    @Test
    fun `non suspend flow method encodes the body and applies interceptors per collection`(): Unit =
        runBlocking {
            val transport = StubTransport()
            val interceptorInvocations = AtomicInteger()
            val api =
                builder(transport)
                    .requestInterceptor(
                        RequestInterceptor {
                            it.header("X-Token", "t-${interceptorInvocations.incrementAndGet()}")
                        })
                    .target(StreamApi::class.java, "http://localhost:1234/")

            val first = api.chat("room 1", ChatRequest("gpt")).toList()
            val second = api.chat("room 1", ChatRequest("gpt")).toList()

            assertThat(first).hasSize(2)
            assertThat(second).hasSize(2)
            // cold stream: every collection re-binds arguments, re-encodes the body and re-runs
            // interceptors
            assertThat(transport.requests).hasSize(2)
            assertThat(interceptorInvocations.get()).isEqualTo(2)
            val body = String(transport.requests.first().body(), Util.UTF_8)
            assertThat(body).isEqualTo(Gson().toJson(ChatRequest("gpt")))
            assertThat(transport.requests.first().url())
                .isEqualTo("http://localhost:1234/chat/room%201")
            assertThat(transport.requests.first().headers()["X-Token"]).containsExactly("t-1")
            assertThat(transport.requests.last().headers()["X-Token"]).containsExactly("t-2")
        }

    @Test
    fun `options argument overrides the builder options on the streaming path`(): Unit = runBlocking {
        val transport = StubTransport()
        val api =
            builder(transport)
                .options(Request.Options(1_000, 2_000))
                .target(StreamApi::class.java, "http://localhost:1234")

        api.withOptions(Request.Options(3_000, 4_000)).toList()

        assertThat(transport.optionsSeen.single().connectTimeoutMillis()).isEqualTo(3_000)
        assertThat(transport.optionsSeen.single().readTimeoutMillis()).isEqualTo(4_000)
    }

    @Test
    fun `builder options are used when the method has no options argument`(): Unit = runBlocking {
        val transport = StubTransport()
        val api =
            builder(transport)
                .options(Request.Options(1_000, 2_000))
                .target(StreamApi::class.java, "http://localhost:1234")

        api.events("x").toList()

        assertThat(transport.optionsSeen.single().connectTimeoutMillis()).isEqualTo(1_000)
    }

    @Test
    fun `cancelling collection cancels the transport subscription`(): Unit = runTest {
        val transport = HangingTransport()
        val api = builder(transport).target(StreamApi::class.java, "http://localhost:1234")

        val job = launch { api.events("cancel").collect {} }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        assertThat(transport.cancellations.get()).isEqualTo(1)
    }

    @Test
    fun `flow element type other than SseEvent is rejected at target creation`() {
        assertThatThrownBy {
            builder(StubTransport()).target(FlowOfStringApi::class.java, "http://localhost:1234")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Flow<SseEvent>")
    }

    @Test
    fun `suspend method that does not return a flow is rejected at target creation`() {
        assertThatThrownBy {
            builder(StubTransport()).target(NonFlowSuspendApi::class.java, "http://localhost:1234")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Flow<SseEvent>")
    }

    @Test
    fun `java default method annotated with sse is rejected at target creation`() {
        assertThatThrownBy {
            builder(StubTransport())
                .target(SseDefaultMethodApi::class.java, "http://localhost:1234")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("default method")
    }

    @Test
    fun `sse methods without a configured transport fail with an explanatory error`() {
        assertThatThrownBy { builder(null).target(StreamApi::class.java, "http://localhost:1234") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("no SSE transport is configured")
            .hasMessageContaining("sseTransport")
    }

    @Test
    fun `buffered suspend methods on the same interface keep the regular coroutine behavior`(): Unit =
        runBlocking {
            val server = MockWebServer()
            server.start()
            server.enqueue(MockResponse().setBody("\"hello\""))
            try {
                val api =
                    builder(StubTransport()).target(StreamApi::class.java, server.url("/").toString())

                val buffered: String = api.buffered()

                assertThat(buffered).isEqualTo("\"hello\"")
                assertThat(server.requestCount).isEqualTo(1)
            } finally {
                server.shutdown()
            }
        }
}
