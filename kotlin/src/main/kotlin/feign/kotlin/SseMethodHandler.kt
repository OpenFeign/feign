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
import feign.MethodMetadata
import feign.QueryMapEncoder
import feign.Request
import feign.RequestInterceptor
import feign.RequestTemplateFactory
import feign.Target
import feign.codec.Encoder
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow

/**
 * Executes an [Sse]-annotated interface method as a cold [Flow] of [SseEvent]s.
 *
 * On every collection the method arguments are re-bound through Feign's native request pipeline
 * ([RequestTemplateFactory]), [RequestInterceptor]s and the [Target] are applied, and the
 * resolved request is handed to the [SseTransport], which decides how much of the declared
 * [Request.Options] applies to a stream. No retry is performed: a stream that already delivered
 * events is never silently replayed. Feign's [feign.Logger] is not invoked on this path; request
 * logging is the transport's responsibility.
 */
class SseMethodHandler
internal constructor(
    private val configKey: String,
    private val target: Target<*>,
    private val buildTemplate: RequestTemplateFactory,
    private val transport: SseTransport,
    private val options: Request.Options,
    private val requestInterceptors: List<RequestInterceptor>,
) {

  /** Returns a cold [Flow]; each collection binds the arguments and sends a fresh request. */
  fun invoke(argv: Array<Any?>): Any = flow {
    val template = buildTemplate.create(argv)
    requestInterceptors.forEach { it.apply(template) }
    val request = target.apply(template)
    emitAll(transport.stream(request, findOptions(argv), configKey).asFlow())
  }

  // Same convention as AsynchronousMethodHandler: an Options argument overrides the builder's.
  private fun findOptions(argv: Array<Any?>): Request.Options =
      argv.firstOrNull { it is Request.Options } as Request.Options? ?: options
}

/**
 * Builder-side configuration captured from [CoroutineFeign.CoroutineBuilder] so that [Sse]
 * methods can be routed to an [SseMethodHandler] when a proxy is created. Instances are created
 * by [CoroutineFeign] itself; they are not meant to be constructed directly.
 */
class SseMethodSupport
internal constructor(
    private val transport: SseTransport,
    private val contract: Contract,
    private val encoder: Encoder,
    private val queryMapEncoder: QueryMapEncoder,
    private val options: Request.Options,
    private val requestInterceptors: List<RequestInterceptor>,
) {

  /** Creates a handler for every [Sse] method of `target.type()`, or an empty map for none. */
  fun handlersFor(target: Target<*>): Map<Method, SseMethodHandler> {
    val sseMethods = sseMethods(target.type())
    if (sseMethods.isEmpty()) {
      return emptyMap()
    }
    // Reuses the builder's contract so a target is parsed exactly the way the buffered
    // methods of the same interface are.
    val metadataByMethod: Map<Method, MethodMetadata> =
        contract.parseAndValidateMetadata(target.type()).associateBy { it.method() }
    return sseMethods.associateWith { method ->
      val configKey = Feign.configKey(target.type(), method)
      declaredReturnType(method, configKey)
      val metadata =
          metadataByMethod[method]
              ?: throw IllegalStateException(
                  "The contract produced no metadata for the @Sse method: $configKey")
      SseMethodHandler(
          configKey = configKey,
          target = target,
          buildTemplate =
              RequestTemplateFactory.forMethod(target, metadata, encoder, queryMapEncoder),
          transport = transport,
          options = options,
          requestInterceptors = requestInterceptors,
      )
    }
  }

  companion object {

    /**
     * Validates the [Sse] declarations of `target.type()` and returns the handlers to route to.
     *
     * Fails fast when an interface declares [Sse] methods but the builder has no [SseTransport],
     * so the error surfaces at proxy creation instead of on first use.
     */
    @JvmStatic
    fun handlersFor(target: Target<*>, support: SseMethodSupport?): Map<Method, SseMethodHandler> {
      val sseMethods = sseMethods(target.type())
      if (sseMethods.isEmpty()) {
        return emptyMap()
      }
      if (support == null) {
        throw IllegalStateException(
            "The interface ${target.type().name} declares @Sse method(s) ${
                sseMethods.joinToString(", ") { Feign.configKey(target.type(), it) }
            } but no SSE transport is configured. Register one on the builder, for example "
                + "CoroutineFeign.builder().sseTransport(WebClientSseClient(...)); the buffered "
                + "AsyncClient cannot stream.")
      }
      return support.handlersFor(target)
    }

    // Same skips as the base contract: Object methods, statics, synthetic/bridge methods and
    // default methods (default methods are rejected with a dedicated message).
    private fun sseMethods(type: Class<*>): List<Method> =
        type.methods.filter {
          it.declaringClass != Any::class.java &&
              !Modifier.isStatic(it.modifiers) &&
              !it.isSynthetic &&
              !it.isBridge &&
              it.getAnnotation(Sse::class.java) != null
        }

    /**
     * Validates that an [Sse] method returns `Flow<SseEvent>`. `suspend` functions report
     * `java.lang.Object` on the JVM, so their real return type is resolved through
     * kotlin-reflect, the same way [KotlinMethodInfo] does it.
     */
    private fun declaredReturnType(method: Method, configKey: String): Type {
      if (method.isDefault) {
        throw IllegalArgumentException(
            "@Sse does not support default methods: $configKey. Annotate abstract methods only.")
      }
      val declared =
          if (method.isSuspend) {
            method.kotlinMethodReturnType
                ?: throw IllegalArgumentException(
                    "The @Sse method $configKey must be a Kotlin function with a Flow<SseEvent> return type")
          } else {
            method.genericReturnType
          }
      if (declared !is ParameterizedType || declared.rawType != Flow::class.java) {
        throw IllegalArgumentException(
            "The @Sse method $configKey must return Flow<SseEvent> (suspend or non-suspend), "
                + "but declares $declared")
      }
      val elementType = declared.actualTypeArguments.singleOrNull()
      if (elementType != SseEvent::class.java) {
        throw IllegalArgumentException(
            "The @Sse method $configKey must return Flow<SseEvent>, but declares $declared")
      }
      return declared
    }
  }
}
