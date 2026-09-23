package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

fun box(): String {
    val backend = RecordingBackend(Level.INFO)
    val logger = Log.named("fixture")
    val effects = mutableListOf<String>()

    fun fields(): Map<String, Any?> {
        effects += "fields"
        return mapOf("k" to 1)
    }

    LogScope(backend).run {
        logger.debug(fields = fields(), message = "constant")
    }

    assertEquals("DEBUG", backend.resolutions.joinToString(",") { it.level.name })
    assertEquals(listOf("fields"), effects)
    assertEquals("", backend.records.joinToString(",") { it.message })
    return "OK"
}
