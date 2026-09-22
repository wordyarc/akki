package fixture

import io.akki.*
import io.akki.test.*

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
        logger.debug(fields = mapOf("k" to mark("debug-fields")), message = "debug-named")
        logger.info("info-${mark("info")}")
        logger.warn("warn-${mark("warn")}")
        "enabled=${logger.isEnabled(Level.TRACE)},sink=${logger.sink(Level.DEBUG) != null}"
    }

    return listOf(
        backend.records.map { it.message }.joinToString(","),
        effects.joinToString(","),
        gate,
    ).joinToString("|")
}
