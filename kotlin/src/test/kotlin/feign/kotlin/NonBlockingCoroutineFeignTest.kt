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

import feign.AsyncClient
import feign.RequestLine
import feign.Response
import feign.codec.Decoder
import feign.http2client.Http2Client
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicReference
import feign.core.codec.DefaultDecoder

/**
 * Demonstrates combining [CoroutineFeign] with a non-blocking [feign.AsyncClient]
 * implementation: the request executes on the HTTP client's own executor and the
 * caller thread is never parked waiting for I/O.
 */
class NonBlockingCoroutineFeignTest {

  private lateinit var server: MockWebServer

  @BeforeEach
  fun beforeEach() {
    server = MockWebServer()
    server.start()
  }

  @AfterEach
  fun afterEach() {
    server.close()
  }

  interface TestApi {
    @RequestLine("GET /")
    suspend fun get(): String
  }

  @Test
  fun `suspend calls with Http2Client decode off the caller thread`() {
    server.enqueue(MockResponse.Builder().body("\"hello\"").build())

    val decodingThread = AtomicReference<Thread>()
    @Suppress("UNCHECKED_CAST")
    val api = CoroutineFeign.builder<TestApi>()
      .client(Http2Client() as AsyncClient<TestApi>)
      .decoder(
        object : Decoder {
          private val delegate: Decoder = DefaultDecoder()

          override fun decode(response: Response, type: Type): Any {
            decodingThread.set(Thread.currentThread())
            return delegate.decode(response, type)
          }
        })
      .target(TestApi::class.java, server.url("/").toString())

    val callerThread = Thread.currentThread()
    val body = runBlocking { api.get() }

    assertThat(body).isEqualTo("\"hello\"")
    // The response was decoded on the async client's completion thread,
    // not on the thread that invoked the suspend function.
    assertThat(decodingThread.get()).isNotSameAs(callerThread)
  }
}
