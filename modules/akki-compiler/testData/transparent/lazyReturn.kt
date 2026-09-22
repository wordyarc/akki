package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

private fun early(logger: Logger): String {
    logger.info { return "early" }
    return "late"
}

fun box(): String {
    assertEquals("early", early(RecordingLogger(minLevel = Level.INFO)))
    assertEquals("late", early(RecordingLogger(minLevel = Level.ERROR)))
    return "OK"
}
