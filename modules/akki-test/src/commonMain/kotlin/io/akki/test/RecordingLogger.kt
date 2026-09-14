package io.akki.test

import io.akki.Level
import io.akki.Logger

public class RecordingLogger(
    override val name: String = "recording",
    private val minLevel: Level = Level.TRACE,
) : Logger() {
    private val recorded: RecordLog<LogRecord> = RecordLog()

    public val records: List<LogRecord>
        get() = recorded.snapshot

    override fun isEnabled(level: Level): Boolean = level >= minLevel

    override fun emit(
        level: Level,
        message: String,
        cause: Throwable?,
        fields: Map<String, Any?>,
    ) {
        recorded.add(LogRecord(name, level, message, cause, fields))
    }

    override fun toString(): String = "RecordingLogger($name)"
}
