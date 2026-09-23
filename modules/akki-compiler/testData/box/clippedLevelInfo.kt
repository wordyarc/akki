// MIN_LEVEL: INFO
// CHECK_BYTECODE_TEXT
// 0 debug-\\u0001
// 1 info-\\u0001
// 2 io/akki/backend/Sink\.emit
package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

private val effects = mutableListOf<String>()

private fun mark(effect: String): String {
    effects += effect
    return effect
}

private fun selectLog(logger: Logger): Logger {
    effects += "receiver"
    return logger
}

@Suppress("LOGGING_CALL_REMOVED")
private fun suppressed(logger: Logger) {
    logger.debug("suppressed")
}

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")

    val gate = withBackend(backend) {
        logger.trace("trace-${mark("trace")}")
        logger.debug { "debug-${mark("debug-lazy")}" }
        selectLog(logger).debug("receiver-${mark("receiver-message")}")
        logger.debug(fields = mapOf("k" to mark("debug-fields")), message = "debug-named")
        suppressed(logger)
        logger.info("info-${mark("info")}")
        logger.warn("warn-${mark("warn")}")
        "enabled=${logger.isEnabled(Level.TRACE)},sink=${logger.sink(Level.DEBUG) != null}"
    }

    assertEquals("info-info,warn-warn", backend.records.joinToString(",") { it.message })
    assertEquals("trace,receiver,receiver-message,debug-fields,info,warn", effects.joinToString(","))
    assertEquals("enabled=true,sink=true", gate)
    return "OK"
}
