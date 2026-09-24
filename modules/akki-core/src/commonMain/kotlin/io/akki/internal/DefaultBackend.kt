package io.akki.internal

import io.akki.Level
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class DefaultBackend(private val notice: String) : LogBackend {
    private val announced = AtomicBoolean(false)

    override fun bind(name: String): LoggerBinding {
        val sinks = Array(Level.entries.size) { ordinal ->
            val level = Level.entries[ordinal]
            Sink { message, cause, fields -> printError(record(level, name, message, cause, fields)) }
        }
        return LoggerBinding { level -> if (level >= THRESHOLD) sinks[level.ordinal] else null }
    }

    private fun record(
        level: Level,
        name: String,
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
        append(name)
        append(" - ")
        append(message)
        if (fields.isNotEmpty()) {
            fields.entries.joinTo(this, prefix = " {", postfix = "}") { (key, value) -> "$key=$value" }
        }
        cause?.let {
            append('\n')
            append(it.stackTraceToString().trimEnd())
        }
    }

    override fun toString(): String = "DefaultBackend"

    private companion object {
        val THRESHOLD: Level = Level.INFO

        val LABELS: List<String> = Level.entries.map { it.name.padEnd(5) }
    }
}
