package fixture

import dev.ashenarx.akki.*
import dev.ashenarx.akki.test.*

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")
    val effects = mutableListOf<String>()

    fun <T> mark(effect: String, value: T): T {
        effects += effect
        return value
    }

    withBackend(backend) {
        logger.warn(
            mark("cause", IllegalStateException("boom")),
            mark("fields", mapOf("k" to 1)),
        ) { mark("message", "lazy") }
    }

    return listOf(
        backend.resolutions.joinToString(",") { it.level.name },
        effects.joinToString(","),
        backend.records.messages.joinToString(","),
    ).joinToString("|")
}
