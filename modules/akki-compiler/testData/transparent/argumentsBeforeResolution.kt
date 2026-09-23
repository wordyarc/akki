package fixture

import io.akki.*
import io.akki.backend.Sink
import io.akki.test.LogRecord
import kotlin.test.assertEquals

fun box(): String {
    val events = mutableListOf<String>()
    val records = mutableListOf<LogRecord>()
    val originalCause = IllegalStateException("original")
    val originalFields = mapOf("value" to "original")
    var message = "original"
    var cause: Throwable = originalCause
    var fields: Map<String, Any?> = originalFields
    var supplier = { "original" }
    val logger = object : Logger() {
        override val name = "freezing"
        override fun sink(level: Level): Sink {
            events += "resolve"
            message = "changed"
            cause = IllegalStateException("changed")
            fields = emptyMap()
            supplier = { "changed" }
            return Sink { text, failure, data -> records += LogRecord(name, level, text, failure, data) }
        }
    }

    logger.info(message, cause, fields)
    cause = originalCause
    fields = originalFields
    supplier = { events += "message body"; "original" }
    logger.info(cause, fields, supplier)
    assertEquals(
        listOf(
            LogRecord("freezing", Level.INFO, "original", originalCause, originalFields),
            LogRecord("freezing", Level.INFO, "original", originalCause, originalFields),
        ),
        records,
    )
    assertEquals(listOf("resolve", "resolve", "message body"), events)
    return "OK"
}
