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

/**
 * Contract wrapper that corrects [MethodMetadata.returnType] for Kotlin `suspend` functions.
 *
 * On the JVM, a `suspend fun foo(): Bar` is compiled with return type `java.lang.Object`, so a
 * plain [feign.Feign.builder] (blocking path) would hand `Object` to [feign.codec.Decoder] for
 * such methods (see issue #2019). This contract re-resolves the return type via kotlin-reflect
 * so that decoders receive the declared Kotlin return type.
 *
 * The [feign.kotlin.CoroutineFeign] path already resolves the correct type through
 * [feign.KotlinMethodInfo]; this contract offers the same behavior to blocking clients.
 */
class KotlinDelegatingContract(private val delegate: Contract) : Contract {

  override fun parseAndValidateMetadata(targetType: Class<*>): List<MethodMetadata> {
    return delegate.parseAndValidateMetadata(targetType).map { metadata ->
      val method = metadata.method()
      if (method != null && method.isSuspend) {
        method.kotlinMethodReturnType?.let { metadata.returnType(it) }
      }
      metadata
    }
  }
}
