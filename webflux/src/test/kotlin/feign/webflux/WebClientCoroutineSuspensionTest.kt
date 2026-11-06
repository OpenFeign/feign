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

import feign.AsyncClient
import feign.RequestLine
import feign.kotlin.CoroutineFeign
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebClientCoroutineSuspensionTest {

  interface TestApi {
    @RequestLine("GET /")
    suspend fun get(): String
  }

  @Test
  fun `spring WebClient configuration is reused and pending IO does not block caller dispatcher`() {
    runBlocking {
      val server = MockWebServer()
      server.start()
      server.enqueue(
        MockResponse()
          .setBody("\"complete\"")
          .setBodyDelay(2, TimeUnit.SECONDS)
      )

      val callerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "feign-caller-dispatcher")
      }
      val callerDispatcher = callerExecutor.asCoroutineDispatcher()

      try {
        val builder = WebClient.builder()
          .filter { request, next ->
            next.exchange(
              ClientRequest.from(request)
                .header("X-WebClient-Filter", "reused")
                .build()
            )
          }

        @Suppress("UNCHECKED_CAST")
        val api = CoroutineFeign.builder<TestApi>()
          .client(WebClientAsyncClient<Any>(builder) as AsyncClient<TestApi>)
          .target(TestApi::class.java, server.url("/").toString())

        val response = async(callerDispatcher) { api.get() }
        val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)
        assertThat(recordedRequest).isNotNull
        assertThat(requireNotNull(recordedRequest).getHeader("X-WebClient-Filter"))
          .isEqualTo("reused")

        val dispatcherTask = async(callerDispatcher) {
          Thread.currentThread().name
        }
        assertThat(withTimeout(500) { dispatcherTask.await() })
          .startsWith("feign-caller-dispatcher")

        assertThat(response.await()).isEqualTo("\"complete\"")
      } finally {
        callerDispatcher.close()
        callerExecutor.shutdownNow()
        server.shutdown()
      }
    }
  }
}
