package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.Logger

internal class LoggerImpl(override val name: String) : Logger {
    override fun isEnabled(level: Level): Boolean =
        platformBackend().resolve(name, level) != null

    override fun emit(
        level: Level,
        message: String,
        fields: Map<String, Any?>,
        cause: Throwable?,
    ): Unit {
        platformBackend().resolve(name, level)?.emit(message, fields, cause)
    }

    override fun toString(): String = "Logger($name)"
}
