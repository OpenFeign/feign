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
import feign.MethodMetadata
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlinx.coroutines.flow.Flow

/** Rejects invalid [Sse] declarations when the proxy is created, before any request is sent. */
internal class SseContract(
    private val delegate: Contract,
    private val sseClientConfigured: Boolean,
) : Contract {

  override fun parseAndValidateMetadata(targetType: Class<*>): List<MethodMetadata> {
    val metadataList = delegate.parseAndValidateMetadata(targetType)
    rejectDefaultSseMethods(targetType)
    val sseMetadata = metadataList.filter { it.method().isAnnotationPresent(Sse::class.java) }
    if (sseMetadata.isEmpty()) {
      return metadataList
    }
    check(sseClientConfigured) {
      val sseMethodKeys = sseMetadata.joinToString(", ") { it.configKey() }
      "The interface ${targetType.name} declares @Sse method(s) $sseMethodKeys but no SSE " +
          "client is configured. Register one on the builder, for example " +
          "CoroutineFeign.builder().sseClient(WebClientSseClient()); the buffered " +
          "AsyncClient cannot stream."
    }
    sseMetadata.forEach { requireFlowOfSseEvent(it.method(), it.configKey()) }
    return metadataList
  }

  private fun rejectDefaultSseMethods(targetType: Class<*>) {
    targetType.methods
        .firstOrNull { it.isDefault && it.isAnnotationPresent(Sse::class.java) }
        ?.let {
          throw IllegalArgumentException(
              "@Sse does not support default methods: ${targetType.simpleName}#${it.name}. "
                  + "Annotate abstract methods only.")
        }
  }

  private fun requireFlowOfSseEvent(method: Method, configKey: String) {
    val returnType = if (method.isSuspend) method.kotlinMethodReturnType else method.genericReturnType
    if (returnType !is ParameterizedType ||
        returnType.rawType != Flow::class.java ||
        returnType.actualTypeArguments.singleOrNull() != SseEvent::class.java) {
      throw IllegalArgumentException(
          "The @Sse method $configKey must return Flow<SseEvent> (suspend or non-suspend), "
              + "but declares $returnType")
    }
  }
}
