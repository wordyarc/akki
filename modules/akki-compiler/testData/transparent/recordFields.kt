// WITH_HELPERS
@file:OptIn(io.akki.ExperimentalAkkiApi::class)

package fixture

import helpers.Effects
import io.akki.*
import io.akki.test.RecordingLogger
import kotlin.test.assertEquals

context(_: RecordScope)
private fun requestFields(id: String) {
    fields("requestId" to id, "tenant" to "acme")
}

private class Owner(private val logger: Logger) {
    fun run(items: List<String>) {
        items.forEach { logger.info { field("item", it); "$it of ${this::class.simpleName}" } }
    }
}

private fun early(logger: Logger, items: List<Int>): String {
    for (item in items) {
        logger.info {
            if (item == 2) return "returned at $item"
            field("item", item)
            "item"
        }
    }
    return "finished"
}

fun box(): String {
    val logger = RecordingLogger("records", minLevel = Level.INFO)
    val effects = Effects()

    logger.info {
        logger.warn { field("inner", effects.mark("inner field", 1)); "inner" }
        field("outer", effects.mark("outer field", 2))
        "outer"
    }
    logger.debug { field("disabled", effects.mark("disabled field")); effects.mark("disabled message") }
    logger.info { requestFields("r-1"); field("tenant", "other"); "helper" }
    Owner(logger).run(listOf("a"))
    logger.info(fields = effects.mark("argument", mapOf("a" to 1, "b" to 1))) {
        field("b", effects.mark("replaced field", 2))
        "merged"
    }
    assertEquals("returned at 2", early(logger, listOf(1, 2, 3)))

    assertEquals(listOf("inner field", "outer field", "argument", "replaced field"), effects.toList())
    assertEquals(
        listOf(
            "inner" to listOf("inner" to 1),
            "outer" to listOf("outer" to 2),
            "helper" to listOf("requestId" to "r-1", "tenant" to "other"),
            "a of Owner" to listOf("item" to "a"),
            "merged" to listOf("a" to 1, "b" to 2),
            "item" to listOf("item" to 1),
        ),
        logger.records.map { it.message to it.fields.toList() },
    )
    return "OK"
}
