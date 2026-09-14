package fixture

import io.akki.*
import io.akki.test.*

private fun <T : () -> String> viaTypeParameter(logger: Logger, message: T) {
    logger.info(message = message)
}

fun box(): String {
    val logger = RecordingLogger()
    viaTypeParameter(logger) { "via-type-parameter" }
    return logger.records.map { it.message }.joinToString(",")
}
