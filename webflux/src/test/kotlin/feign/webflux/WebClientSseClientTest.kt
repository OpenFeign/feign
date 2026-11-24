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
package feign.webflux

import com.fasterxml.jackson.databind.ObjectMapper
import feign.AsyncClient
import feign.FeignException
import feign.Headers
import feign.Param
import feign.Request
import feign.RequestInterceptor
import feign.RequestLine
import feign.codec.ErrorDecoder
import feign.jackson.JacksonEncoder
import feign.kotlin.CoroutineFeign
import feign.kotlin.Sse
import feign.kotlin.SseEvent
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

/**
 * End-to-end streaming behavior of {@link WebClientSseClient} behind {@code CoroutineFeign}
 * {@code @Sse} methods, against MockWebServer and a raw socket server.
 */
class WebClientSseClientTest {

  interface StreamApi {
    @RequestLine("GET /events")
    @Headers("Accept: text/event-stream")
    @Sse
    fun streamEvents(): Flow<SseEvent>

    @RequestLine("GET /events")
    @Headers("Accept: text/event-stream")
    @Sse
    suspend fun streamEventsSuspending(): Flow<SseEvent>

    @RequestLine("POST /chat/{room}")
    @Headers("Content-Type: application/json")
    @Sse
    fun streamChat(@Param("room") room: String, body: ChatRequest): Flow<SseEvent>

    @RequestLine("GET /buffered")
    suspend fun fetchBufferedBody(): String
  }

  data class ChatRequest(val model: String, val temperature: Double)

  private lateinit var server: MockWebServer

  @BeforeEach
  fun startMockServer() {
    server = MockWebServer()
    server.start()
  }

  @AfterEach
  fun shutDownMockServer() {
    server.shutdown()
  }

  private fun newStreamApiClient(
      baseUrl: String = server.url("/").toString(),
      configure: CoroutineFeign.CoroutineBuilder<Any?>.() ->
      CoroutineFeign.CoroutineBuilder<Any?> = { this }
  ): StreamApi {
    @Suppress("UNCHECKED_CAST")
    return CoroutineFeign.builder<Any?>()
        .client(WebClientAsyncClient<Any>() as AsyncClient<Any?>)
        .sseClient(WebClientSseClient())
        .encoder(JacksonEncoder(ObjectMapper()))
        .configure()
        .target(StreamApi::class.java, baseUrl)
  }

  private fun createChunkedSseResponse(body: String): MockResponse =
      MockResponse()
          .setHeader("Content-Type", "text/event-stream")
          .setChunkedBody(body, 4)
          .setBodyDelay(20, TimeUnit.MILLISECONDS)

  @Test
  fun `fragmented response with multiple events and metadata decodes into separate events`(): Unit =
      runBlocking {
        server.enqueue(
            createChunkedSseResponse("id: 7\nevent: delta\ndata: one\ndata: two\nretry: 5000\n\ndata: last\n\n"))

        val events =
            withTimeout(10_000) { newStreamApiClient().streamEventsSuspending().toList() }

        assertThat(events)
            .containsExactly(
                SseEvent("one\ntwo", "delta", "7", Duration.ofSeconds(5)),
                SseEvent("last"))
      }

  @Test
  fun `non suspend flow method decodes events and sends the Accept header`(): Unit = runBlocking {
    server.enqueue(createChunkedSseResponse("data: hello\n\n"))

    val events = withTimeout(10_000) { newStreamApiClient().streamEvents().toList() }

    assertThat(events).containsExactly(SseEvent("hello"))
    val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
    assertThat(recordedRequest.getHeader("Accept")).isEqualTo("text/event-stream")
  }

  @Test
  fun `the flow is cold and each collection sends a new request`(): Unit = runBlocking {
    server.enqueue(createChunkedSseResponse("data: first-run\n\n"))
    server.enqueue(createChunkedSseResponse("data: second-run\n\n"))

    val events = newStreamApiClient().streamEvents()

    val firstRun = withTimeout(10_000) { events.toList() }
    val secondRun = withTimeout(10_000) { events.toList() }

    assertThat(firstRun).containsExactly(SseEvent("first-run"))
    assertThat(secondRun).containsExactly(SseEvent("second-run"))
    assertThat(server.requestCount).isEqualTo(2)
  }

  @Test
  fun `post sse method expands path param, encodes json body, and applies headers and interceptors`(): Unit =
      runBlocking {
        server.enqueue(createChunkedSseResponse("data: ok\n\n"))

        val events =
            newStreamApiClient(
                    configure = {
                      requestInterceptor(
                          RequestInterceptor { it.header("X-Auth", "local-credential") })
                    })
                .streamChat("general", ChatRequest("gpt", 0.7))
                .toList()

        assertThat(events).containsExactly(SseEvent("ok"))
        val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertThat(recordedRequest.path).isEqualTo("/chat/general")
        assertThat(recordedRequest.getHeader("Content-Type")).startsWith("application/json")
        assertThat(recordedRequest.getHeader("X-Auth")).isEqualTo("local-credential")
        assertThat(recordedRequest.body.readUtf8())
            .isEqualTo("{\"model\":\"gpt\",\"temperature\":0.7}")
      }

  @Test
  fun `http error status surfaces as feign exception with the response body`(): Unit =
      runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("service exploded"))

        val exception =
            try {
              withTimeout(10_000) { newStreamApiClient().streamEvents().toList() }
              null
            } catch (e: FeignException) {
              e
            }!!

        assertThat(exception.status()).isEqualTo(500)
        assertThat(exception.contentUTF8()).isEqualTo("service exploded")
        assertThat(exception.message).contains("StreamApi#streamEvents")
      }

  @Test
  fun `http errors use the configured Feign error decoder`() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(422).setBody("invalid event request"))
    val apiWithErrorDecoder =
        newStreamApiClient(
            configure = {
              errorDecoder(
                  ErrorDecoder { methodKey, response ->
                    assertThat(methodKey).contains("StreamApi#streamEvents")
                    assertThat(response.status()).isEqualTo(422)
                    val body = response.body().asInputStream().use { it.readBytes().decodeToString() }
                    assertThat(body).isEqualTo("invalid event request")
                    IllegalArgumentException("decoded ${response.status()}")
                  })
            })

    val exception =
        try {
          withTimeout(10_000) { apiWithErrorDecoder.streamEvents().toList() }
          null
        } catch (e: IllegalArgumentException) {
          e
        }!!

    assertThat(exception).hasMessage("decoded 422")
  }

  @Test
  fun `first event is delivered while the response is still open and events arrive one per request`() {
    val rawServer = SseTestServer()
    try {
      val firstEventConsumed = CountDownLatch(1)
      thread {
        val connection = rawServer.awaitConnection()
        connection.awaitRequestHeaders()
        connection.writeResponseHeaders()
        connection.writeRaw("data: first\n\n")
        // only write more after the consumer has actually received the first event
        firstEventConsumed.await()
        connection.writeRaw("data: second\n\n")
        connection.close()
      }

      // Demand is granted event by event: the second event is only written after the consumer
      // has received the first one, proving the first event crossed the wire while the response
      // was still open. The trailing request lets the collector observe the upstream completion
      // (the kotlinx flow bridge only delivers it on the next demand signal).
      StepVerifier.create(newStreamApiClient(rawServer.baseUrl).streamEvents().asFlux(), 0)
          .thenRequest(1)
          .assertNext { event ->
            assertThat(event.data).isEqualTo("first")
            firstEventConsumed.countDown()
          }
          .thenRequest(1)
          .assertNext { event -> assertThat(event.data).isEqualTo("second") }
          .thenRequest(1)
          .expectComplete()
          .verify(Duration.ofSeconds(10))
    } finally {
      rawServer.close()
    }
  }

  @Test
  fun `cancelling the flow disposes the connection while the server is still streaming`() {
    val rawServer = SseTestServer()
    try {
      val acceptedConnection = AtomicReference<SseTestConnection>()
      thread {
        val connection = rawServer.awaitConnection()
        acceptedConnection.set(connection)
        connection.awaitRequestHeaders()
        connection.writeResponseHeaders()
        connection.writeRaw("data: first\n\n")
        connection.writeRaw("data: second\n\n")
        // hold the response open; the client is expected to cancel mid-stream
      }

      StepVerifier.create(newStreamApiClient(rawServer.baseUrl).streamEvents().asFlux(), 1)
          .expectNextCount(1)
          .thenCancel()
          .verify(Duration.ofSeconds(10))

      val connection = acceptedConnection.get()
      val clientDisconnected = connection.awaitClientDisconnect()
      assertThat(clientDisconnected)
          .describedAs("the transport must close the connection on cancellation")
          .isTrue()
    } finally {
      rawServer.close()
    }
  }

  @Test
  fun `buffered methods on the same interface still aggregate through the async client`(): Unit =
      runBlocking {
        server.enqueue(MockResponse().setBody("\"plain\""))

        val body = withTimeout(10_000) { newStreamApiClient().fetchBufferedBody() }

        assertThat(body).isEqualTo("\"plain\"")
      }
}
