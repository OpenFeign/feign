@file:JvmName("MethodKt")

package feign.kotlin

import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

val Method.kotlinMethodReturnType: Type?
    get() = kotlinFunction?.returnType?.javaType
