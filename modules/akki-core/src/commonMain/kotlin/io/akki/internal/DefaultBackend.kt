package io.akki.internal

import io.akki.Level
import io.akki.LogBackend
import io.akki.Sink
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class DefaultBackend : LogBackend {
    private val announced = AtomicBoolean(false)

    override fun isEnabled(name: String, level: Level): Boolean = level >= Level.INFO

    override fun resolve(name: String, level: Level): Sink? {
        if (level < Level.INFO) return null
        return Sink { message, cause, fields -> printError(record(level, name, message, cause, fields)) }
    }

    private fun record(
        level: Level,
        name: String,
        message: String,
        cause: Throwable?,
        fields: Map<String, Any?>,
    ): String = buildString {
        if (!announced.load() && announced.compareAndSet(false, true)) {
            append(NOTICE)
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

    private companion object {
        const val NOTICE: String =
            "akki: no backend installed, writing to stderr at INFO. Install one with Log.install(backend)."

        val LABELS: List<String> = Level.entries.map { it.name.padEnd(5) }
    }
}
