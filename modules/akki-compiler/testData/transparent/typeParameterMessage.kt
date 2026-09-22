package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

private fun <T : () -> String> viaTypeParameter(logger: Logger, message: T) {
    logger.info(message = message)
}

fun box(): String {
    val logger = RecordingLogger()
    viaTypeParameter(logger) { "via-type-parameter" }
    assertEquals("via-type-parameter", logger.records.joinToString(",") { it.message })
    return "OK"
}
