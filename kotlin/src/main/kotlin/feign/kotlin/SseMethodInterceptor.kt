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

import feign.FeignException
import feign.Request
import feign.RequestInterceptor
import feign.RequestTemplate
import feign.codec.ErrorDecoder
import feign.interceptor.Invocation
import feign.interceptor.MethodInterceptor
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow

/**
 * Answers [Sse] methods with a cold [Flow] of [SseEvent]s instead of a buffered call.
 *
 * Each collection copies the bound request template, runs the [RequestInterceptor]s, applies the
 * target and subscribes to the [SseClient]. Feign's [feign.Logger] and [feign.Retryer] are not
 * used on this path.
 */
internal class SseMethodInterceptor(
    private val sseClient: SseClient,
    private val defaultOptions: Request.Options,
    private val errorDecoder: ErrorDecoder,
    private val requestInterceptors: List<RequestInterceptor>,
) : MethodInterceptor {

  override fun intercept(invocation: Invocation, chain: MethodInterceptor.Chain): Any? {
    val method = invocation.method()
    if (!method.isAnnotationPresent(Sse::class.java)) {
      return chain.next(invocation)
    }
    val events = createEventFlow(invocation)
    return if (method.isSuspend) CompletableFuture.completedFuture(events) else events
  }

  private fun createEventFlow(invocation: Invocation): Flow<SseEvent> {
    val boundTemplate = invocation.requestTemplate()
    val configKey = invocation.methodMetadata().configKey()
    val options = findOptionsOrDefault(invocation.arguments())
    return flow {
      val template = RequestTemplate.from(boundTemplate)
      requestInterceptors.forEach { it.apply(template) }
      val request = invocation.target().apply(template)
      emitAll(sseClient.createEventPublisher(request, options).asFlow())
    }
        .catch { error ->
          if (error !is SseErrorResponseException) throw error
          throw errorDecoder.decode(configKey, error.response)
              ?: FeignException.errorStatus(configKey, error.response)
        }
  }

  private fun findOptionsOrDefault(arguments: Array<Any?>?): Request.Options =
      arguments?.firstOrNull { it is Request.Options } as Request.Options? ?: defaultOptions
}
