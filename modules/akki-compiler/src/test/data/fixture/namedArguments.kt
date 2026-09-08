package fixture

import io.akki.*
import io.akki.test.*

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend(setOf(Level.TRACE, Level.INFO, Level.WARN, Level.ERROR))
    val logger = Log.named("fixture")
    val effects = mutableListOf<String>()

    fun fields(): Map<String, Any?> {
        effects += "fields"
        return mapOf("k" to 1)
    }

    withBackend(backend) {
        logger.debug(fields = fields(), message = "constant")
    }

    return listOf(
        backend.resolutions.joinToString(",") { it.level.name },
        effects.joinToString(","),
        backend.records.messages.joinToString(","),
    ).joinToString("|")
}
