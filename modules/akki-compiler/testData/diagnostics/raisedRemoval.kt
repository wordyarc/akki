// RUN_PIPELINE_TILL: BACKEND
// MIN_LEVEL: INFO
// WARNING_LEVEL: LOGGING_CALL_REMOVED:warning
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
package fixture

import io.akki.*

fun probe(logger: Logger) {
    logger.<!LOGGING_CALL_REMOVED!>debug("raised")<!>
    logger.info("kept")
}
