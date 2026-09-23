package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")
    val effects = mutableListOf<String>()

    fun <T> mark(effect: String, value: T): T {
        effects += effect
        return value
    }

    LogScope(backend).run {
        logger.warn(
            mark("cause", IllegalStateException("boom")),
            mark("fields", mapOf("k" to 1)),
        ) { mark("message", "lazy") }
    }

    assertEquals("WARN", backend.resolutions.joinToString(",") { it.level.name })
    assertEquals("cause,fields,message", effects.joinToString(","))
    assertEquals("lazy", backend.records.joinToString(",") { it.message })
    return "OK"
}
