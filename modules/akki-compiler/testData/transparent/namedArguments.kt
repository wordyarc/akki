package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend(Level.INFO)
    val logger = Log.named("fixture")
    val effects = mutableListOf<String>()

    fun fields(): Map<String, Any?> {
        effects += "fields"
        return mapOf("k" to 1)
    }

    withBackend(backend) {
        logger.debug(fields = fields(), message = "constant")
    }

    assertEquals("DEBUG", backend.resolutions.joinToString(",") { it.level.name })
    assertEquals(listOf("fields"), effects)
    assertEquals("", backend.records.joinToString(",") { it.message })
    return "OK"
}
