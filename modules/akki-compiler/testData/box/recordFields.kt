// DUMP_KT_IR
// CHECK_BYTECODE_TEXT
// 4 INVOKESPECIAL io/akki/RecordScope\.<init>
// 6 io/akki/backend/Sink\.emit
// 1 kotlin/jvm/functions/Function1\.invoke
@file:OptIn(io.akki.ExperimentalAkkiApi::class)

package fixture

import io.akki.*
import io.akki.test.RecordingLogger
import kotlin.test.assertEquals

context(_: RecordScope)
private fun requestFields(id: String) {
    fields("requestId" to id, "tenant" to "acme")
}

fun box(): String {
    val logger = RecordingLogger("records")
    val value: context(RecordScope) () -> String = { field("source", "value"); "from a value" }

    logger.info { "plain" }
    logger.info { field("attempt", 2); "one field" }
    logger.warn(IllegalStateException("boom")) { requestFields("r-1"); "helper" }
    logger.error(message = value)
    logger.info(fields = mapOf("jobId" to "j-1")) { "argument only" }
    logger.info(fields = mapOf("jobId" to "j-1", "attempt" to 1)) { field("attempt", 2); "merged" }

    assertEquals(
        listOf(
            "plain" to emptyMap(),
            "one field" to mapOf("attempt" to 2),
            "helper" to mapOf("requestId" to "r-1", "tenant" to "acme"),
            "from a value" to mapOf("source" to "value"),
            "argument only" to mapOf("jobId" to "j-1"),
            "merged" to mapOf("jobId" to "j-1", "attempt" to 2),
        ),
        logger.records.map { it.message to it.fields },
    )
    return "OK"
}
