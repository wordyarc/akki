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

    @InternalAkkiApi
    public fun sink(level: Level): Sink? =
        if (isEnabled(level)) {
            Sink { message, cause, fields -> emit(level, message, cause, fields) }
        } else {
            null
        }

    public fun trace(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.TRACE, message, cause, fields)

    @OptIn(InternalAkkiApi::class)
    public fun trace(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.TRACE)?.emit(message(), cause, fields)
    }

    public fun debug(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.DEBUG, message, cause, fields)

    @OptIn(InternalAkkiApi::class)
    public fun debug(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.DEBUG)?.emit(message(), cause, fields)
    }

    public fun info(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.INFO, message, cause, fields)

    @OptIn(InternalAkkiApi::class)
    public fun info(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.INFO)?.emit(message(), cause, fields)
    }

    public fun warn(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.WARN, message, cause, fields)

    @OptIn(InternalAkkiApi::class)
    public fun warn(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.WARN)?.emit(message(), cause, fields)
    }

    public fun error(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit = emit(Level.ERROR, message, cause, fields)

    @OptIn(InternalAkkiApi::class)
    public fun error(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.ERROR)?.emit(message(), cause, fields)
    }
}
