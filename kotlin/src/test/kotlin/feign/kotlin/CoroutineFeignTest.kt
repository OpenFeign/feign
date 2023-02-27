/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.kotlin

import com.google.gson.Gson
import com.google.gson.JsonIOException
import feign.Param
import feign.QueryMapEncoder
import feign.RequestInterceptor
import feign.RequestLine
import feign.Response
import feign.Util
import feign.codec.Decoder
import feign.codec.Encoder
import feign.codec.ErrorDecoder
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Type

class CoroutineFeignTest {
    @Test
    fun `sut should run correctly when response is basic type`(): Unit = runBlocking {
        // Arrange
        val server = MockWebServer()
        val expected = "Hello Worlda"
        server.enqueue(MockResponse().setBody(expected))
        val client = TestInterfaceAsyncBuilder()
            .target("http://localhost:" + server.port)

        // Act
        val firstOrder: String = client.findOrderThatReturningBasicType(orderId = 1)

        // Assert
        assertThat(firstOrder).isEqualTo(expected)
    }

    @Test
    fun `sut should run correctly when response is complex type`(): Unit = runBlocking {
        // Arrange
        val server = MockWebServer()
        val expected = IceCreamOrder(
            id = "HELLO WORLD",
            no = 999,
        )
        server.enqueue(MockResponse().setBody("{ id: '${expected.id}', no: '${expected.no}'}"))

        val client = TestInterfaceAsyncBuilder()
            .decoder(GsonDecoder())
            .target("http://localhost:" + server.port)

        // Act
        val firstOrder: IceCreamOrder = client.findOrderThatReturningComplexType(orderId = 1)

        // Assert
        assertThat(firstOrder).isEqualTo(expected)
    }

    @Test
    fun `sut should run correctly when empty response is represented by java_lang_Void`(): Unit = runBlocking {
        // Arrange
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("HELLO WORLD"))

        val client = TestInterfaceAsyncBuilder()
            .target("http://localhost:" + server.port)

        // Act
        val firstOrder: Void = client.findOrderThatReturningVoid(orderId = 1)

        // Assert
        assertThat(firstOrder).isNull()
    }

    @Test
    fun `sut should run correctly when empty response is represented by kotlin_Unit`(): Unit = runBlocking {
        // Arrange
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("HELLO WORLD"))

        val client = TestInterfaceAsyncBuilder()
            .target("http://localhost:" + server.port)

        // Act
        val firstOrder: Unit = client.findOrderThatReturningUnit(orderId = 1)

        // Assert
        assertThat(firstOrder).isEqualTo(Unit)
    }

    @Test
    fun `sut should run correctly when using http body`(): Unit = runBlocking {
        // Arrange
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("HELLO WORLD"))

        val client = TestInterfaceAsyncBuilder()
            .target("http://localhost:" + server.port)

        // Act
        val firstOrder = client.findOrderWithHttpBody(
            order = IceCreamOrder(
                id = "1",
                no = 2,
            )
        )

        // Assert
        assertThat(firstOrder).isEqualTo(Unit)
    }

    internal class GsonDecoder : Decoder {
        private val gson = Gson()

        override fun decode(response: Response, type: Type): Any? {
            if (Void.TYPE == type || response.body() == null) {
                return null
            }
            val reader = response.body().asReader(Util.UTF_8)
            return try {
                gson.fromJson<Any>(reader, type)
            } catch (e: JsonIOException) {
                if (e.cause != null && e.cause is IOException) {
                    throw IOException::class.java.cast(e.cause)
                }
                throw e
            } finally {
                Util.ensureClosed(reader)
            }
        }
    }

    internal class TestInterfaceAsyncBuilder {
        private val delegate = CoroutineFeign.builder<Void>()
            .decoder(Decoder.Default()).encoder { `object`, bodyType, template ->
                if (`object` is Map<*, *>) {
                    template.body(Gson().toJson(`object`))
                } else {
                    template.body(`object`.toString())
                }
            }

        fun requestInterceptor(requestInterceptor: RequestInterceptor?): TestInterfaceAsyncBuilder {
            delegate.requestInterceptor(requestInterceptor)
            return this
        }

        fun encoder(encoder: Encoder?): TestInterfaceAsyncBuilder {
            delegate.encoder(encoder)
            return this
        }

        fun decoder(decoder: Decoder?): TestInterfaceAsyncBuilder {
            delegate.decoder(decoder)
            return this
        }

        fun errorDecoder(errorDecoder: ErrorDecoder?): TestInterfaceAsyncBuilder {
            delegate.errorDecoder(errorDecoder)
            return this
        }

        fun dismiss404(): TestInterfaceAsyncBuilder {
            delegate.dismiss404()
            return this
        }

        fun queryMapEndcoder(queryMapEncoder: QueryMapEncoder?): TestInterfaceAsyncBuilder {
            delegate.queryMapEncoder(queryMapEncoder)
            return this
        }

        fun target(url: String?): TestInterfaceAsync {
            return delegate.target(TestInterfaceAsync::class.java, url)
        }
    }

    internal interface TestInterfaceAsync {
        @RequestLine("GET /icecream/orders/{orderId}")
        suspend fun findOrderThatReturningBasicType(@Param("orderId") orderId: Int): String

        @RequestLine("GET /icecream/orders/{orderId}")
        suspend fun findOrderThatReturningComplexType(@Param("orderId") orderId: Int): IceCreamOrder

        @RequestLine("GET /icecream/orders/{orderId}")
        suspend fun findOrderThatReturningVoid(@Param("orderId") orderId: Int): Void

        @RequestLine("GET /icecream/orders/{orderId}")
        suspend fun findOrderThatReturningUnit(@Param("orderId") orderId: Int): Unit

        @RequestLine("POST /icecream/orders")
        suspend fun findOrderWithHttpBody(order: IceCreamOrder): Unit
    }

    data class IceCreamOrder(
        val id: String,
        val no: Long,
    )
}
