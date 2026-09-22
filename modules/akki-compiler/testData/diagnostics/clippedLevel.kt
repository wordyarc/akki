// RUN_PIPELINE_TILL: BACKEND
// MIN_LEVEL: INFO
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
package fixture

import io.akki.*

private fun mark(effect: String): String = effect

@Suppress("LOGGING_CALL_REMOVED")
fun suppressed(logger: Logger) {
    logger.debug("suppressed")
}

fun reported(logger: Logger) {
    logger.<!LOGGING_CALL_REMOVED!>trace("trace-${mark("trace")}")<!>
    logger.<!LOGGING_CALL_REMOVED!>debug { "debug-${mark("debug-lazy")}" }<!>
    logger.<!LOGGING_CALL_REMOVED!>debug(fields = mapOf("k" to mark("debug-fields")), message = "debug-named")<!>
    logger.info("info-${mark("info")}")
    logger.warn("warn-${mark("warn")}")
}
