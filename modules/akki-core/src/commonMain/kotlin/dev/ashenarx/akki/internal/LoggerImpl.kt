package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.InternalAkkiApi
import dev.ashenarx.akki.Logger
import dev.ashenarx.akki.Sink

@OptIn(InternalAkkiApi::class)
internal class LoggerImpl(override val name: String) : Logger() {
    override fun isEnabled(level: Level): Boolean =
        platformBackend().isEnabled(name, level)

    override fun sink(level: Level): Sink? = platformBackend().resolve(name, level)

    override fun emit(
        level: Level,
        message: String,
        cause: Throwable?,
        fields: Map<String, Any?>,
    ): Unit {
        platformBackend().resolve(name, level)?.emit(message, cause, fields)
    }

    override fun toString(): String = "Logger($name)"
}
