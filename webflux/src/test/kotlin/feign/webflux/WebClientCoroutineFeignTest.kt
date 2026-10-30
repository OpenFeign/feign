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

import feign.RequestLine
import feign.Response
import feign.codec.Decoder
import feign.kotlin.CoroutineFeign
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicReference

/**
 * Demonstrates combining [feign.kotlin.CoroutineFeign] with the WebClient based
 * [WebClientAsyncClient]: the request runs on Reactor Netty's event loop and the caller thread is
 * never parked waiting for I/O.
 */
class WebClientCoroutineFeignTest {

  interface TestApi {
    @RequestLine("GET /")
    suspend fun get(): String
  }

  @Test
  fun `suspend calls with WebClientAsyncClient decode off the caller thread`() {
    val server = MockWebServer()
    server.start()
    server.enqueue(MockResponse().setBody("\"hello\""))

    val decodingThread = AtomicReference<Thread>()
    @Suppress("UNCHECKED_CAST")
    val api = CoroutineFeign.builder<TestApi>()
      .client(WebClientAsyncClient<Any>() as feign.AsyncClient<TestApi>)
      .decoder(
        object : Decoder {
          private val delegate: Decoder = Decoder.Default()

          override fun decode(response: Response, type: Type): Any {
            decodingThread.set(Thread.currentThread())
            return delegate.decode(response, type)
          }
        })
      .target(TestApi::class.java, server.url("/").toString())

    val callerThread = Thread.currentThread()
    val body = runBlocking { api.get() }

    assertThat(body).isEqualTo("\"hello\"")
    // The response was decoded on Reactor Netty's event loop, not on the thread
    // that invoked the suspend function.
    assertThat(decodingThread.get()).isNotSameAs(callerThread)
    assertThat(decodingThread.get().name).contains("reactor-http")

    server.shutdown()
  }
}
