package dev.ashenarx.lokki.internal

import dev.ashenarx.lokki.LogBackend
import dev.ashenarx.lokki.Logger
import kotlin.reflect.KClass

internal expect fun platformLogger(name: String): Logger

internal expect fun platformBackend(): LogBackend

internal expect fun installPlatformBackend(backend: LogBackend)

internal expect fun platformCallerLogger(): Logger

internal expect fun platformTypeName(type: KClass<*>): String
