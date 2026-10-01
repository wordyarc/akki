package io.akki.internal

import io.akki.Logger
import io.akki.backend.LogBackend
import java.util.concurrent.ConcurrentHashMap

private val loggers: ConcurrentHashMap<String, LoggerImpl> = ConcurrentHashMap()

internal actual fun getOrCreateLogger(name: String): Logger = loggers.getOrPut(name) { LoggerImpl(name) }

internal actual fun unbindLoggers(backend: LogBackend) {
    loggers.values.forEach { it.unbind(backend) }
}
