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

/**
 * Marks a Feign method as a Server-Sent Events (SSE) source.
 *
 * The method must return `kotlinx.coroutines.flow.Flow<SseEvent>`. It can be a `suspend` function
 * or a regular function. With [CoroutineFeign] and an [SseClient], each collection of the returned
 * flow sends one HTTP request and emits the events as they arrive. The response body is never
 * buffered.
 *
 * ```kotlin
 * interface AiApi {
 *     @RequestLine("POST /chat/completions")
 *     @Headers("Content-Type: application/json", "Accept: text/event-stream")
 *     @Sse
 *     fun chat(body: ChatRequest): Flow<SseEvent>
 * }
 * ```
 *
 * Methods without [Sse] keep the regular buffered coroutine behavior.
 */
@Experimental
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Sse
