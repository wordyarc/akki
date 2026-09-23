package fixture

import io.akki.*
import io.akki.test.RecordingLogger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun smartCast(logger: Logger, text: String?): String {
    logger.debug(text!!)
    return text
}

private fun contract(logger: Logger, text: String?): String {
    logger.debug(checkNotNull(text))
    return text
}

private fun assignment(logger: Logger): String {
    val result: String
    logger.debug(run {
        result = "assigned"
        "message"
    })
    return result
}

private fun terminated(logger: Logger): String {
    logger.debug("message", cause = error("cause"))
}

private fun returned(logger: Logger): String {
    logger.debug(run<String> { return "returned" })
}

private fun uninitializedMessage(logger: Logger) {
    lateinit var message: String
    logger.debug(message)
}

private fun uninitializedSupplier(logger: Logger) {
    lateinit var message: () -> String
    logger.debug(message = message)
}

private fun uninitializedReceiver() {
    lateinit var logger: Logger
    logger.debug("message")
}

fun box(): String {
    for (threshold in listOf(Level.TRACE, Level.INFO)) {
        val logger = RecordingLogger(minLevel = threshold)
        assertEquals("text", smartCast(logger, "text"))
        assertFailsWith<NullPointerException> { smartCast(logger, null) }
        assertEquals("text", contract(logger, "text"))
        assertFailsWith<IllegalStateException> { contract(logger, null) }
        assertEquals("assigned", assignment(logger))
        assertEquals("cause", assertFailsWith<IllegalStateException> { terminated(logger) }.message)
        assertEquals("returned", returned(logger))
        assertFailsWith<UninitializedPropertyAccessException> { uninitializedMessage(logger) }
        assertFailsWith<UninitializedPropertyAccessException> { uninitializedSupplier(logger) }
    }
    assertFailsWith<UninitializedPropertyAccessException> { uninitializedReceiver() }
    return "OK"
}
