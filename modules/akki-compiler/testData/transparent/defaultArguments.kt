package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

fun box(): String {
    val logger = Log.named("fixture")
    val cause = IllegalStateException("boom")
    val fields = mapOf("k" to 1)

    val records = recordLogs {
        logger.info("eager")
        logger.info { "lazy" }
        logger.warn("eager cause", cause)
        logger.warn(cause) { "lazy cause" }
        logger.error("eager fields", fields = fields)
        logger.error(fields = fields) { "lazy fields" }
    }

    assertEquals(
        listOf(
            LogRecord("fixture", Level.INFO, "eager"),
            LogRecord("fixture", Level.INFO, "lazy"),
            LogRecord("fixture", Level.WARN, "eager cause", cause),
            LogRecord("fixture", Level.WARN, "lazy cause", cause),
            LogRecord("fixture", Level.ERROR, "eager fields", fields = fields),
            LogRecord("fixture", Level.ERROR, "lazy fields", fields = fields),
        ),
        records,
    )
    return "OK"
}
