package io.akki

public abstract class Logger {
    public abstract val name: String

    public abstract fun isEnabled(level: Level): Boolean

    public abstract fun emit(
        level: Level,
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit

    private val sinks: Array<Sink> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Array(Level.entries.size) { ordinal ->
            val level = Level.entries[ordinal]
            Sink { message, cause, fields -> emit(level, message, cause, fields) }
        }
    }

    @InternalAkkiApi
    public open fun sink(level: Level): Sink? = if (isEnabled(level)) sinks[level.ordinal] else null

    @Suppress("NOTHING_TO_INLINE")
    @OptIn(InternalAkkiApi::class)
    public inline fun trace(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit {
        sink(Level.TRACE)?.emit(message, cause, fields)
    }

    @OptIn(InternalAkkiApi::class)
    public inline fun trace(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.TRACE)?.emit(message(), cause, fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    @OptIn(InternalAkkiApi::class)
    public inline fun debug(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit {
        sink(Level.DEBUG)?.emit(message, cause, fields)
    }

    @OptIn(InternalAkkiApi::class)
    public inline fun debug(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.DEBUG)?.emit(message(), cause, fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    @OptIn(InternalAkkiApi::class)
    public inline fun info(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit {
        sink(Level.INFO)?.emit(message, cause, fields)
    }

    @OptIn(InternalAkkiApi::class)
    public inline fun info(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.INFO)?.emit(message(), cause, fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    @OptIn(InternalAkkiApi::class)
    public inline fun warn(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit {
        sink(Level.WARN)?.emit(message, cause, fields)
    }

    @OptIn(InternalAkkiApi::class)
    public inline fun warn(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.WARN)?.emit(message(), cause, fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    @OptIn(InternalAkkiApi::class)
    public inline fun error(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit {
        sink(Level.ERROR)?.emit(message, cause, fields)
    }

    @OptIn(InternalAkkiApi::class)
    public inline fun error(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ): Unit {
        sink(Level.ERROR)?.emit(message(), cause, fields)
    }
}
