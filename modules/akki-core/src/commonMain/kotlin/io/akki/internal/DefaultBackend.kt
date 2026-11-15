@file:OptIn(InternalAkkiApi::class)

package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.Level
import io.akki.LogContext
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class DefaultBackend(private val notice: String) : LogBackend {
    private val announced = AtomicBoolean(false)

    override fun bind(loggerName: String): LoggerBinding {
        val sinks = Array(Level.entries.size) { ordinal ->
            val level = Level.entries[ordinal]
            Sink { message, cause, fields -> printlnToStdErr(format(level, loggerName, message, cause, fields)) }
        }
        return LoggerBinding { level -> if (level >= THRESHOLD) sinks[level.ordinal] else null }
    }

    private fun format(
        level: Level,
        loggerName: String,
        message: String,
        cause: Throwable?,
        fields: Map<String, Any?>,
    ): String = buildString {
        if (!announced.load() && announced.compareAndSet(false, true)) {
            append(notice)
            append('\n')
        }
        append(LABELS[level.ordinal])
        append(' ')
        append(loggerName)
        append(" - ")
        append(message)
        appendFields(LogContext.current(), fields)
        cause?.let {
            append('\n')
            append(it.stackTraceToString().trimEnd())
        }
    }

    private fun StringBuilder.appendFields(context: LogContext, fields: Map<String, Any?>) {
        if (context.fields.isEmpty() && fields.isEmpty()) return
        var separator = " {"
        context.forEachField(fields) { key, value ->
            append(separator).append(key).append('=').append(value)
            separator = ", "
        }
        append('}')
    }

    override fun toString(): String = "DefaultBackend"

    private companion object {
        val THRESHOLD: Level = Level.INFO

        val LABELS: List<String> = Level.entries.map { it.name.padEnd(5) }
    }
}
