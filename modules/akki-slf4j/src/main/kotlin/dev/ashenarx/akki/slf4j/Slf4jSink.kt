package dev.ashenarx.akki.slf4j

import dev.ashenarx.akki.Sink
import org.slf4j.Logger
import org.slf4j.event.Level
import org.slf4j.spi.CallerBoundaryAware

internal class Slf4jSink(
    private val logger: Logger,
    private val level: Level,
) : Sink {
    override fun emit(message: String, cause: Throwable?, fields: Map<String, Any?>) {
        val event = logger.atLevel(level)
        if (event is CallerBoundaryAware) event.setCallerBoundary(CALLER_BOUNDARY)
        cause?.let(event::setCause)
        fields.forEach { (key, value) -> event.addKeyValue(key, value) }
        event.log(message)
    }

    private companion object {
        val CALLER_BOUNDARY: String = Slf4jSink::class.java.name
    }
}
