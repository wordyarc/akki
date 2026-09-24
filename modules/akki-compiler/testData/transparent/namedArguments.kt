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

    withLogScope(LogScope(effects.markingResolutions(backend))) {
        logger.debug(fields = effects.mark("fields", mapOf("k" to 1)), message = "constant")
    }

    assertEquals(listOf("fields", "resolve DEBUG"), effects.toList())
    assertEquals("", backend.records.joinToString(",") { it.message })
    return "OK"
}
