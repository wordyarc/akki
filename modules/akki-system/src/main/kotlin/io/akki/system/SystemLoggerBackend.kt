package io.akki.system

import io.akki.Level
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import java.lang.System.Logger.Level as SystemLevel

public class SystemLoggerBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding = SystemLoggerBinding(System.getLogger(loggerName))

    override fun toString(): String = "SystemLoggerBackend"
}

private class SystemLoggerBinding(private val logger: System.Logger) : LoggerBinding {
    private val sinks: Array<Sink> = Array(LEVELS.size) { SystemLoggerSink(logger, LEVELS[it]) }

    override fun resolve(level: Level): Sink? =
        if (logger.isLoggable(LEVELS[level.ordinal])) sinks[level.ordinal] else null
}

private val LEVELS: Array<SystemLevel> = Array(Level.entries.size) { ordinal ->
    when (Level.entries[ordinal]) {
        Level.TRACE -> SystemLevel.TRACE
        Level.DEBUG -> SystemLevel.DEBUG
        Level.INFO -> SystemLevel.INFO
        Level.WARN -> SystemLevel.WARNING
        Level.ERROR -> SystemLevel.ERROR
    }
}
