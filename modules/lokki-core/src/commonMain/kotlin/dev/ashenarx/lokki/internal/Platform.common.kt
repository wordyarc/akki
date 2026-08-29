package dev.ashenarx.lokki.internal

import dev.ashenarx.lokki.Log
import dev.ashenarx.lokki.LogBackend
import kotlin.reflect.KClass

internal expect fun platformLog(name: String): Log

internal expect fun platformBackend(): LogBackend

internal expect fun installPlatformBackend(backend: LogBackend)

internal expect fun platformCallerLog(): Log

internal expect fun platformTypeName(type: KClass<*>): String
