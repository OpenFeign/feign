@file:JvmName("MethodKt")

package feign

import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

internal fun Method.isSuspend(): Boolean =
    kotlinFunction?.isSuspend ?: false

internal val Method.kotlinMethodReturnType: Type?
    get() = kotlinFunction?.returnType?.javaType
