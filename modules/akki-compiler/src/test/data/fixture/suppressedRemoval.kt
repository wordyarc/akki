package fixture

import io.akki.*
import io.akki.test.*

@Suppress("LOGGING_CALL_REMOVED")
private fun suppressed(logger: Logger) {
    logger.debug("suppressed")
}

private fun reported(logger: Logger) {
    logger.debug("reported")
}

fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")

    withBackend(backend) {
        suppressed(logger)
        reported(logger)
    }

    return backend.records.map { it.message }.joinToString(",")
}
