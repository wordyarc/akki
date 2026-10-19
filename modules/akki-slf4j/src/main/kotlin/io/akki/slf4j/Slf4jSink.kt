package io.akki.slf4j

import io.akki.LogContext
import io.akki.backend.Sink
import org.slf4j.Logger
import org.slf4j.spi.CallerBoundaryAware
import org.slf4j.event.Level as Slf4jLevel

internal class Slf4jSink(
    private val logger: Logger,
    private val level: Slf4jLevel,
) : Sink {
    override fun emit(message: String, cause: Throwable?, fields: Map<String, Any?>) {
        val event = logger.atLevel(level)
        if (event is CallerBoundaryAware) event.setCallerBoundary(CALLER_BOUNDARY)
        cause?.let(event::setCause)
        LogContext.current().fields.forEach { (key, value) -> if (key !in fields) event.addKeyValue(key, value) }
        fields.forEach { (key, value) -> event.addKeyValue(key, value) }
        event.log(message)
    }

    private companion object {
        val CALLER_BOUNDARY: String = Slf4jSink::class.java.name
    }
}
