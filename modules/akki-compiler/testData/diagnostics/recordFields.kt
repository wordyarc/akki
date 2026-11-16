// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*

fun withoutOptIn(logger: Logger) {
    logger.info { <!OPT_IN_USAGE_ERROR!>field<!>("jobId", 1); "one field" }
    logger.info { <!OPT_IN_USAGE_ERROR!>fields<!>("jobId" to 1); "several fields" }
    logger.info(fields = mapOf("jobId" to 1)) { "fields as an argument" }
}

@OptIn(ExperimentalAkkiApi::class)
fun outsideRecord() {
    <!NO_CONTEXT_ARGUMENT!>field<!>("jobId", 1)
    <!NO_CONTEXT_ARGUMENT!>fields<!>("jobId" to 1)
}

fun plainFunction(logger: Logger, message: () -> String) {
    logger.<!NONE_APPLICABLE!>info<!>(message = message)
    logger.info { message() }
}
