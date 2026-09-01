package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Logger
import java.util.concurrent.ConcurrentHashMap

private object JvmLoggerRegistry {
    private val loggers: ConcurrentHashMap<String, Logger> = ConcurrentHashMap()

    fun logger(name: String): Logger {
        freezeJvmLoggerNameStyle()
        return loggers.computeIfAbsent(name, ::LoggerImpl)
    }
}

internal actual fun platformLogger(name: String): Logger = JvmLoggerRegistry.logger(name)
