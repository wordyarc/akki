package dev.ashenarx.lokki

public interface Logger {
    public val name: String

    public fun isEnabled(level: Level): Boolean

    public fun emit(
        level: Level,
        message: String,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit

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
}
