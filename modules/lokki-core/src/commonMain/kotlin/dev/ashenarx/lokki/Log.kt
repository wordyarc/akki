package dev.ashenarx.lokki

import dev.ashenarx.lokki.internal.LogRegistry
import dev.ashenarx.lokki.internal.installPlatformBackend
import dev.ashenarx.lokki.internal.platformBackend
import dev.ashenarx.lokki.internal.platformTypeName
import kotlin.reflect.KClass

public class Log internal constructor(public val name: String) {
    public fun isEnabled(level: Level): Boolean =
        platformBackend().resolve(name, level) != null

    public fun emit(
        level: Level,
        message: String,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit {
        platformBackend().resolve(name, level)?.emit(level, message, fields)
    }

    public fun trace(message: String, fields: Map<String, Any?> = emptyMap()): Unit =
        emit(Level.TRACE, message, fields)

    public fun debug(message: String, fields: Map<String, Any?> = emptyMap()): Unit =
        emit(Level.DEBUG, message, fields)

    public fun info(message: String, fields: Map<String, Any?> = emptyMap()): Unit =
        emit(Level.INFO, message, fields)

    public fun warn(message: String, fields: Map<String, Any?> = emptyMap()): Unit =
        emit(Level.WARN, message, fields)

    public fun error(message: String, fields: Map<String, Any?> = emptyMap()): Unit =
        emit(Level.ERROR, message, fields)

    override fun toString(): String = "Log($name)"

    public companion object {
        public fun of(type: KClass<*>): Log = LogRegistry.of(platformTypeName(type))

        public inline fun <reified T : Any> of(): Log = of(T::class)

        public fun named(name: String): Log = LogRegistry.of(name)

        public fun install(backend: LogBackend): Unit = installPlatformBackend(backend)
    }
}
