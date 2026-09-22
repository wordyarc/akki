// MIN_LEVEL: OFF
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

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")

    val gate = withBackend(backend) {
        logger.trace("trace-${mark("trace")}")
        logger.debug { "debug-${mark("debug-lazy")}" }
        selectLog(logger).debug("receiver-${mark("receiver-message")}")
        logger.info("info-${mark("info")}")
        logger.error("error-${mark("error")}")
        "enabled=${logger.isEnabled(Level.TRACE)},sink=${logger.sink(Level.DEBUG) != null}"
    }

    assertEquals("", backend.records.joinToString(",") { it.message })
    assertEquals("receiver", effects.joinToString(","))
    assertEquals("enabled=true,sink=true", gate)
    return "OK"
}
