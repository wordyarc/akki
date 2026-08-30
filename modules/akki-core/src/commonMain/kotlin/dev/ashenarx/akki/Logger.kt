package dev.ashenarx.akki

public interface Logger {
    public val name: String

    public fun isEnabled(level: Level): Boolean

    public fun emit(
        level: Level,
        message: String,
        fields: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    ): Unit

    public fun trace(
        message: String,
        fields: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    ): Unit = emit(Level.TRACE, message, fields, cause)

    public fun debug(
        message: String,
        fields: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    ): Unit = emit(Level.DEBUG, message, fields, cause)

    public fun info(
        message: String,
        fields: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    ): Unit = emit(Level.INFO, message, fields, cause)

    public fun warn(
        message: String,
        fields: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    ): Unit = emit(Level.WARN, message, fields, cause)

    public fun error(
        message: String,
        fields: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    ): Unit = emit(Level.ERROR, message, fields, cause)
}
