// WITH_HELPERS
// MIN_LEVEL: OFF
package fixture

import helpers.Effects
import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")
    val effects = Effects()

    val gate = withLogScope(LogScope(backend)) {
        logger.trace("trace-${effects.mark("trace")}")
        logger.debug { "debug-${effects.mark("debug-lazy")}" }
        effects.mark("receiver", logger).debug("receiver-${effects.mark("receiver-message")}")
        logger.info("info-${effects.mark("info")}")
        logger.error("error-${effects.mark("error")}")
        "enabled=${logger.isEnabled(Level.TRACE)},sink=${logger.sink(Level.DEBUG) != null}"
    }

    assertEquals("", backend.records.joinToString(",") { it.message })
    assertEquals("trace,receiver,receiver-message,info,error", effects.toString())
    assertEquals("enabled=true,sink=true", gate)
    return "OK"
}
