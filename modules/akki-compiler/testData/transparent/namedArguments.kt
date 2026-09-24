// WITH_HELPERS
package fixture

import helpers.Effects
import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

fun box(): String {
    val backend = RecordingBackend(Level.INFO)
    val logger = Log.named("fixture")
    val effects = Effects()

    withLogScope(LogScope(backend)) {
        logger.debug(fields = effects.mark("fields", mapOf("k" to 1)), message = "constant")
    }

    assertEquals("DEBUG", backend.resolutions.joinToString(",") { it.level.name })
    assertEquals(listOf("fields"), effects.toList())
    assertEquals("", backend.records.joinToString(",") { it.message })
    return "OK"
}
