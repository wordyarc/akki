package io.akki.internal

import io.akki.Logger
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import kotlin.reflect.KClass

internal expect fun getOrCreateLogger(name: String): Logger

internal expect fun discoverPlatformBackend(): LogBackend?

internal expect fun unbindLoggers(backend: LogBackend)

internal expect fun loggerName(sourceName: String, jvmClassName: String): String

internal expect fun loggerName(kClass: KClass<*>): String

internal expect fun printlnToStdErr(message: String)

internal expect val Throwable.isFatal: Boolean

internal expect val Throwable.isPermanent: Boolean

internal expect class ThreadSlot<T : Any>() {
    fun get(): T?

    fun set(value: T?)
}

internal expect class ScopeBindings() {
    operator fun get(name: String): LoggerBinding?

    fun putIfAbsent(name: String, binding: LoggerBinding): LoggerBinding?
}
