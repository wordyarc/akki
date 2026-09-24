package io.akki.internal

import io.akki.LogScope
import io.akki.Logger
import io.akki.backend.LogBackend
import kotlin.reflect.KClass

internal expect fun platformLogger(name: String): Logger

internal expect fun discoverPlatformBackend(): LogBackend

internal expect fun releasePlatformBackend(backend: LogBackend)

internal expect fun platformDeclarationName(sourceName: String, jvmClassName: String): String

internal expect fun platformTypeName(type: KClass<*>): String

internal expect fun printError(message: String)

internal expect fun Throwable.isFatal(): Boolean

internal expect fun currentEntry(): LogScope.Entry?

internal expect fun setCurrentEntry(entry: LogScope.Entry?)
