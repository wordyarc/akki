package dev.ashenarx.akki

public interface Logger {
    public val name: String

    public fun isEnabled(level: Level): Boolean

    public fun emit(
        level: Level,
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit

    public fun trace(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.TRACE, message, cause, fields)

    public fun debug(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.DEBUG, message, cause, fields)

    public fun info(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.INFO, message, cause, fields)

    public fun warn(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.WARN, message, cause, fields)

    public fun error(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.ERROR, message, cause, fields)
}
