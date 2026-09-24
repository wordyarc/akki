package io.akki.test

import io.akki.Level
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink

public class RecordingBackend(
    private val minLevel: Level = Level.TRACE,
) : LogBackend {
    private val recorded: RecordLog<LogRecord> = RecordLog()

    public val records: List<LogRecord>
        get() = recorded.snapshot

    override fun bind(name: String): LoggerBinding {
        val sinks = Array(Level.entries.size) { ordinal ->
            val level = Level.entries[ordinal]
            Sink { message, cause, fields -> recorded.add(LogRecord(name, level, message, cause, fields)) }
        }
        return LoggerBinding { level -> if (level >= minLevel) sinks[level.ordinal] else null }
    }

    override fun toString(): String = "RecordingBackend(minLevel=$minLevel, records=${records.size})"
}
