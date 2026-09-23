package io.akki.internal

import io.akki.Logger
import io.akki.backend.LogBackend
import java.util.concurrent.ConcurrentHashMap

private val loggers: ConcurrentHashMap<String, LoggerImpl> = ConcurrentHashMap()

internal actual fun platformLogger(name: String): Logger = loggers.computeIfAbsent(name, ::LoggerImpl)

internal actual fun releasePlatformBackend(backend: LogBackend) {
    loggers.values.forEach { it.release(backend) }
}
