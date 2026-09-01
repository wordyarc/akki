package dev.ashenarx.akki.slf4j

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.Sink
import org.slf4j.LoggerFactory
import org.slf4j.event.Level as Slf4jLevel

public object Slf4jBackend : LogBackend {
    override fun resolve(name: String, level: Level): Sink? {
        val logger = LoggerFactory.getLogger(name)
        val slf4jLevel = level.toSlf4j()
        return if (logger.isEnabledForLevel(slf4jLevel)) Slf4jSink(logger, slf4jLevel) else null
    }
}

internal fun Level.toSlf4j(): Slf4jLevel = when (this) {
    Level.TRACE -> Slf4jLevel.TRACE
    Level.DEBUG -> Slf4jLevel.DEBUG
    Level.INFO -> Slf4jLevel.INFO
    Level.WARN -> Slf4jLevel.WARN
    Level.ERROR -> Slf4jLevel.ERROR
}
