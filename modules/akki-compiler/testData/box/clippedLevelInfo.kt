// DUMP_KT_IR
// WITH_HELPERS
// TREAT_AS_ONE_FILE
// MIN_LEVEL: INFO
// CHECK_BYTECODE_TEXT
// 0 debug-\\u0001
// 1 info-\\u0001
// 2 io/akki/backend/Sink\.emit
package fixture

import helpers.Effects
import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

@Suppress("LOGGING_CALL_REMOVED")
private fun suppressed(logger: Logger) {
    logger.debug("suppressed")
}

fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")
    val effects = Effects()

    val gate = withLogScope(LogScope(backend)) {
        logger.trace("trace-${effects.mark("trace")}")
        logger.debug { "debug-${effects.mark("debug-lazy")}" }
        effects.mark("receiver", logger).debug("receiver-${effects.mark("receiver-message")}")
        logger.debug(fields = mapOf("k" to effects.mark("debug-fields")), message = "debug-named")
        suppressed(logger)
        logger.info("info-${effects.mark("info")}")
        logger.warn("warn-${effects.mark("warn")}")
        "enabled=${logger.isEnabled(Level.TRACE)},sink=${logger.sink(Level.DEBUG) != null}"
    }

    assertEquals("info-info,warn-warn", backend.records.joinToString(",") { it.message })
    assertEquals("trace,receiver,receiver-message,debug-fields,info,warn", effects.toString())
    assertEquals("enabled=true,sink=true", gate)
    return "OK"
}
