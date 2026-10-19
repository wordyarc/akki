package io.akki.backend

import io.akki.Level

public fun LogBackend.filter(predicate: (loggerName: String, level: Level) -> Boolean): LogBackend =
    FilteredBackend(this, predicate)

private class FilteredBackend(
    private val backend: LogBackend,
    private val predicate: (String, Level) -> Boolean,
) : LogBackend {
    override fun bind(loggerName: String): LoggerBinding {
        val delegate = backend.bind(loggerName)
        return LoggerBinding { level -> if (predicate(loggerName, level)) delegate.resolve(level) else null }
    }

    override fun toString(): String = "Filtered($backend)"
}
