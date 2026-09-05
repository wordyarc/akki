package dev.ashenarx.akki.slf4j

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.Sink
import dev.ashenarx.akki.SinkResolver
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level as Slf4jLevel

public class Slf4jBackend : LogBackend {
    private val bindings: ConcurrentHashMap<String, Slf4jBinding> = ConcurrentHashMap()

    override fun isEnabled(name: String, level: Level): Boolean = binding(name).isEnabled(level)

    override fun resolve(name: String, level: Level): Sink? = binding(name).resolve(level)

    override fun bind(name: String): SinkResolver = binding(name)

    private fun binding(name: String): Slf4jBinding =
        bindings[name] ?: bindings.computeIfAbsent(name, ::Slf4jBinding)
}

private class Slf4jBinding(name: String) : SinkResolver {
    private val logger: Logger = LoggerFactory.getLogger(name)
    private val sinks: Array<Sink> = Array(LEVELS.size) { Slf4jSink(logger, LEVELS[it]) }

    fun isEnabled(level: Level): Boolean = logger.isEnabledForLevel(LEVELS[level.ordinal])

    override fun resolve(level: Level): Sink? = if (isEnabled(level)) sinks[level.ordinal] else null
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
