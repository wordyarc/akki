package io.akki.internal

import io.akki.Log
import io.akki.Logger
import io.akki.backend.LogBackend
import kotlin.reflect.KClass

internal expect fun platformLogger(name: String): Logger

internal expect fun platformBackend(): LogBackend

internal expect fun installPlatformBackend(backend: LogBackend): Log.Installation

internal expect fun platformDeclarationName(source: String, platformName: String): String

internal expect fun platformTypeName(type: KClass<*>): String

internal expect fun printError(message: String)
