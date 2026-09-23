package io.akki.internal

import io.akki.Logger
import io.akki.backend.LogBackend
import kotlin.reflect.KClass

internal expect fun platformLogger(name: String): Logger

internal expect fun discoverPlatformBackend(): LogBackend

internal expect fun releasePlatformBackend(backend: LogBackend)

internal expect fun platformDeclarationName(source: String, platformName: String): String

internal expect fun platformTypeName(type: KClass<*>): String

internal expect fun printError(message: String)
