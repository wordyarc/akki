@file:OptIn(InternalAkkiApi::class)

package io.akki.slf4j

import io.akki.InternalAkkiApi
import io.akki.LogContext
import io.akki.backend.Sink
import io.akki.internal.forEachField
import org.slf4j.Logger
import org.slf4j.MDC
import org.slf4j.spi.CallerBoundaryAware
import org.slf4j.spi.LoggingEventBuilder
import org.slf4j.event.Level as Slf4jLevel

internal class Slf4jSink(
    private val logger: Logger,
    private val level: Slf4jLevel,
    private val target: ContextTarget,
) : Sink {
    override fun emit(message: String, cause: Throwable?, fields: Map<String, Any?>) {
        val event = logger.atLevel(level)
        if (event is CallerBoundaryAware) event.setCallerBoundary(CALLER_BOUNDARY)
        cause?.let(event::setCause)
        val context = LogContext.current()
        when (target) {
            ContextTarget.KEY_VALUE_PAIRS -> {
                context.forEachField(fields) { key, value -> event.addKeyValue(key, value) }
                event.log(message)
            }
            ContextTarget.MDC -> logThroughMdc(event, message, context, fields)
        }
    }

    private fun logThroughMdc(
        event: LoggingEventBuilder,
        message: String,
        context: LogContext,
        fields: Map<String, Any?>,
    ) {
        val capacity = context.fields.size + fields.size
        if (capacity == 0) {
            event.log(message)
            return
        }
        val keys = ArrayList<String>(capacity)
        val previous = ArrayList<String?>(capacity)
        try {
            context.forEachField(fields) { key, value -> put(key, value, keys, previous) }
            event.log(message)
        } finally {
            for (index in keys.indices.reversed()) {
                val before = previous[index]
                if (before == null) MDC.remove(keys[index]) else MDC.put(keys[index], before)
            }
        }
    }

    private fun put(key: String, value: Any?, keys: MutableList<String>, previous: MutableList<String?>) {
        keys += key
        previous += MDC.get(key)
        MDC.put(key, value.toString())
    }

    private companion object {
        val CALLER_BOUNDARY: String = Slf4jSink::class.java.name
    }
}
