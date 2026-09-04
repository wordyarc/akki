package fixture

import dev.ashenarx.akki.*
import dev.ashenarx.akki.test.*

private fun <T : () -> String> viaTypeParameter(logger: Logger, message: T) {
    logger.info(message = message)
}

fun box(): String {
    val logger = RecordingLogger()
    viaTypeParameter(logger) { "via-type-parameter" }
    return logger.records.messages.joinToString(",")
}
