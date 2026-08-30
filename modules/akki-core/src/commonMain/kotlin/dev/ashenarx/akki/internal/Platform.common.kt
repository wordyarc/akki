package dev.ashenarx.akki.internal

import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.Logger
import kotlin.reflect.KClass

internal expect fun platformLogger(name: String): Logger

internal expect fun platformBackend(): LogBackend

internal expect fun installPlatformBackend(backend: LogBackend)

internal expect fun platformCallerLogger(): Logger

internal expect fun platformTypeName(type: KClass<*>): String
