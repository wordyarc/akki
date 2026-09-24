// WITH_HELPERS
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
        logger.debug(fields = mapOf("k" to effects.mark("debug-fields")), message = "debug-named")
        logger.info("info-${effects.mark("info")}")
        logger.warn("warn-${effects.mark("warn")}")
        "enabled=${logger.isEnabled(Level.TRACE)},sink=${logger.sink(Level.DEBUG) != null}"
    }

    assertEquals(
        "trace-trace,debug-debug-lazy,receiver-receiver-message,debug-named,info-info,warn-warn",
        backend.records.joinToString(",") { it.message },
    )
    assertEquals("trace,debug-lazy,receiver,receiver-message,debug-fields,info,warn", effects.toString())
    assertEquals("enabled=true,sink=true", gate)
    return "OK"
}
