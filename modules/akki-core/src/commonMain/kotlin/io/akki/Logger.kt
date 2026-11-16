package io.akki

import io.akki.backend.Sink
import kotlin.jvm.JvmOverloads

public abstract class Logger {
    public abstract val name: String

    public abstract fun sink(level: Level): Sink?

    public fun isEnabled(level: Level): Boolean = sink(level) != null

    @Suppress("NOTHING_TO_INLINE")
    @JvmOverloads
    public inline fun trace(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.TRACE)?.emit(message, cause, fields)
    }

    @JvmOverloads
    public inline fun trace(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: context(RecordScope) () -> String,
    ) {
        val sink = sink(Level.TRACE) ?: return
        val scope = RecordScope(fields)
        sink.emit(message(scope), cause, scope.fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    @JvmOverloads
    public inline fun debug(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.DEBUG)?.emit(message, cause, fields)
    }

    @JvmOverloads
    public inline fun debug(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: context(RecordScope) () -> String,
    ) {
        val sink = sink(Level.DEBUG) ?: return
        val scope = RecordScope(fields)
        sink.emit(message(scope), cause, scope.fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    @JvmOverloads
    public inline fun info(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.INFO)?.emit(message, cause, fields)
    }

    @JvmOverloads
    public inline fun info(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: context(RecordScope) () -> String,
    ) {
        val sink = sink(Level.INFO) ?: return
        val scope = RecordScope(fields)
        sink.emit(message(scope), cause, scope.fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    @JvmOverloads
    public inline fun warn(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.WARN)?.emit(message, cause, fields)
    }

    @JvmOverloads
    public inline fun warn(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: context(RecordScope) () -> String,
    ) {
        val sink = sink(Level.WARN) ?: return
        val scope = RecordScope(fields)
        sink.emit(message(scope), cause, scope.fields)
    }

    @Suppress("NOTHING_TO_INLINE")
    @JvmOverloads
    public inline fun error(
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        sink(Level.ERROR)?.emit(message, cause, fields)
    }

    @JvmOverloads
    public inline fun error(
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
        message: context(RecordScope) () -> String,
    ) {
        val sink = sink(Level.ERROR) ?: return
        val scope = RecordScope(fields)
        sink.emit(message(scope), cause, scope.fields)
    }
}
