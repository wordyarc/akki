package dev.ashenarx.akki.test

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.Logger

public class RecordingLogger(
    override val name: String = "recording",
    private val enabled: Set<Level> = Level.entries.toSet(),
) : Logger() {
    public val records: List<LogRecord>
        field: MutableList<LogRecord> = mutableListOf()

    override fun isEnabled(level: Level): Boolean = level in enabled

    override fun emit(
        level: Level,
        message: String,
        cause: Throwable?,
        fields: Map<String, Any?>,
    ): Unit {
        records += LogRecord(name, level, message, cause, fields)
    }

    override fun toString(): String = "RecordingLogger($name)"
}
