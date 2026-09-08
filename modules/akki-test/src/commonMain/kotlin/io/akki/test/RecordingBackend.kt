package io.akki.test

import io.akki.Level
import io.akki.LogBackend
import io.akki.Sink

public class RecordingBackend(
    private val enabled: Set<Level> = Level.entries.toSet(),
) : LogBackend {
    public val records: List<LogRecord>
        field: MutableList<LogRecord> = mutableListOf()

    public val resolutions: List<Resolution>
        field: MutableList<Resolution> = mutableListOf()

    override fun resolve(name: String, level: Level): Sink? {
        resolutions += Resolution(name, level)
        if (level !in enabled) return null
        return Sink { message, cause, fields -> records += LogRecord(name, level, message, cause, fields) }
    }
}
