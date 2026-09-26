package io.akki.internal

import io.akki.LogScope
import io.akki.Logger
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import kotlin.reflect.KClass
import kotlin.time.Duration

internal expect fun platformLogger(name: String): Logger

internal expect fun discoverPlatformBackend(): LogBackend

internal expect fun releasePlatformBackend(backend: LogBackend)

internal expect fun platformDeclarationName(sourceName: String, jvmClassName: String): String

internal expect fun platformTypeName(type: KClass<*>): String

internal expect fun printError(message: String)

internal expect fun Throwable.isFatal(): Boolean

internal expect fun Throwable.isPermanent(): Boolean

internal expect fun currentEntry(): LogScope.Entry?

internal expect fun setCurrentEntry(entry: LogScope.Entry?)

internal expect fun currentThread(): Any

internal expect class Latch() {
    fun open()

    fun await(timeout: Duration)
}

internal expect class ScopeBindings() {
    operator fun get(name: String): LoggerBinding?

    fun putIfAbsent(name: String, binding: LoggerBinding): LoggerBinding?
}
