package fixture

import io.akki.*
import io.akki.test.*

private fun early(logger: Logger): String {
    logger.info { return "early" }
    return "late"
}

fun box(): String =
    early(RecordingLogger(minLevel = Level.INFO)) + "," + early(RecordingLogger(minLevel = Level.ERROR))
