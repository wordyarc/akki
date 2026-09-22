package io.akki.slf4j

import io.akki.Level
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level as Slf4jLevel

public class Slf4jBackend : LogBackend {
    override fun bind(name: String): LoggerBinding = Slf4jBinding(name)

    override fun toString(): String = "Slf4jBackend"
}

private class Slf4jBinding(name: String) : LoggerBinding {
    private val logger: Logger = LoggerFactory.getLogger(name)
    private val sinks: Array<Sink> = Array(LEVELS.size) { Slf4jSink(logger, LEVELS[it]) }

    override fun resolve(level: Level): Sink? =
        if (logger.isEnabledForLevel(LEVELS[level.ordinal])) sinks[level.ordinal] else null
}

private val LEVELS: Array<Slf4jLevel> = Array(Level.entries.size) { ordinal ->
    when (Level.entries[ordinal]) {
        Level.TRACE -> Slf4jLevel.TRACE
        Level.DEBUG -> Slf4jLevel.DEBUG
        Level.INFO -> Slf4jLevel.INFO
        Level.WARN -> Slf4jLevel.WARN
        Level.ERROR -> Slf4jLevel.ERROR
    }
}
