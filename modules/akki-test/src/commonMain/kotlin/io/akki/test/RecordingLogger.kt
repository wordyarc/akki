package io.akki.test

import io.akki.Level
import io.akki.Logger
import io.akki.backend.Sink

public class RecordingLogger(
    override val name: String = "recording",
    private val minLevel: Level = Level.TRACE,
) : Logger() {
    private val recorded: RecordLog<LogRecord> = RecordLog()

    private val sinks: Array<Sink> = Array(Level.entries.size) { ordinal ->
        val level = Level.entries[ordinal]
        Sink { message, cause, fields -> recorded.add(LogRecord(name, level, message, cause, fields)) }
    }

    public val records: List<LogRecord>
        get() = recorded.snapshot

    override fun sink(level: Level): Sink? = if (level >= minLevel) sinks[level.ordinal] else null

    override fun toString(): String = "RecordingLogger($name)"
}
