// WITH_HELPERS
package fixture

import helpers.Effects
import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")
    val effects = Effects()

    withLogScope(LogScope(backend)) {
        logger.warn(
            effects.mark("cause", IllegalStateException("boom")),
            effects.mark("fields", mapOf("k" to 1)),
        ) { effects.mark("message", "lazy") }
    }

    assertEquals("WARN", backend.resolutions.joinToString(",") { it.level.name })
    assertEquals("cause,fields,message", effects.toString())
    assertEquals("lazy", backend.records.joinToString(",") { it.message })
    return "OK"
}
