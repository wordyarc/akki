package io.akki

import io.akki.backend.Sink

public abstract class Logger {
    public abstract val name: String

    public abstract fun sink(level: Level): Sink?

    public fun isEnabled(level: Level): Boolean = sink(level) != null

    @Suppress("NOTHING_TO_INLINE")
    public inline fun trace(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.TRACE)?.emit(message, cause, fields)
    }

    public inline fun trace(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ) {
        sink(Level.TRACE)?.emit(message(), cause, fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    public inline fun debug(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.DEBUG)?.emit(message, cause, fields)
    }

    public inline fun debug(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ) {
        sink(Level.DEBUG)?.emit(message(), cause, fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    public inline fun info(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.INFO)?.emit(message, cause, fields)
    }

    public inline fun info(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ) {
        sink(Level.INFO)?.emit(message(), cause, fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    public inline fun warn(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.WARN)?.emit(message, cause, fields)
    }

    public inline fun warn(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ) {
        sink(Level.WARN)?.emit(message(), cause, fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    public inline fun error(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.ERROR)?.emit(message, cause, fields)
    }

    public inline fun error(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: () -> String,
    ) {
        sink(Level.ERROR)?.emit(message(), cause, fields)
    }
}
