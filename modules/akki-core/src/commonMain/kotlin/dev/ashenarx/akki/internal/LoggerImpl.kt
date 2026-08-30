package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.Logger

internal class LoggerImpl(override val name: String) : Logger {
    override fun isEnabled(level: Level): Boolean =
        platformBackend().resolve(name, level) != null

    override fun emit(level: Level, message: String, fields: Map<String, Any?>): Unit {
        platformBackend().resolve(name, level)?.emit(level, message, fields)
    }

    override fun toString(): String = "Logger($name)"
}
