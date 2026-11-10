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

import feign.Contract
import feign.Feign
import feign.RequestLine
import feign.codec.Decoder
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import feign.core.DefaultContract

/**
 * Reproduction test for https://github.com/OpenFeign/feign/issues/2019
 * Verifies what `Type` a Decoder receives when decoding a response of a suspend function.
 */
class SuspendDecoderTypeTest {

    private lateinit var server: MockWebServer
    private val recordedTypes: MutableList<Type> = mutableListOf()

    @BeforeEach
    fun beforeEach() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun afterEach() {
        server.close()
    }

    @Test
    fun `decoder should receive the suspend function return type, not Object`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"name":"ok"}""").build())

        val api = CoroutineFeign.builder<Void>()
            .decoder { response, type ->
                recordedTypes.add(type)
                response.close()
                TestObject("ok")
            }
            .target(TestApi::class.java, server.url("/").toString())

        val result = api.suspendFun()

        assertEquals("ok", result.name)
        assertEquals(1, recordedTypes.size)
        val received = recordedTypes[0]
        assertTrue(
            received == TestObject::class.java,
            "Expected decoder type to be TestObject but was: $received"
        )
    }

    @Test
    fun `blocking builder with KotlinDelegatingContract passes suspend return type to decoder`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"name":"ok"}""").build())

        val api = Feign.builder()
            .contract(KotlinDelegatingContract(DefaultContract()))
            .decoder { response, type ->
                recordedTypes.add(type)
                response.close()
                TestObject("ok")
            }
            .target(TestApi::class.java, server.url("/").toString())

        val result = api.suspendFun()

        assertEquals("ok", result.name)
        assertEquals(1, recordedTypes.size)
        val received = recordedTypes[0]
        assertTrue(
            received == TestObject::class.java,
            "Expected decoder type to be TestObject but was: $received"
        )
    }

    interface TestApi {
        @RequestLine("GET /test")
        suspend fun suspendFun(): TestObject
    }

    data class TestObject(val name: String)
}
