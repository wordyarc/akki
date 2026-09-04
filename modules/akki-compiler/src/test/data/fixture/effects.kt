package fixture

import dev.ashenarx.akki.*
import dev.ashenarx.akki.test.*

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend(setOf(Level.INFO, Level.WARN, Level.ERROR))
    val logger = Log.named("fixture")
    val effects = mutableListOf<String>()
    var sequence = 0

    fun mark(effect: String): Int {
        effects += effect
        return ++sequence
    }

    fun cause(): Throwable = IllegalStateException("cause-${mark("cause")}")
    fun fields(): Map<String, Any?> = mapOf("value" to mark("fields"))
    fun selectedLogger() = logger.also { effects += "receiver" }
    val variableMessage: () -> String = { "variable-${mark("variable-message")}" }

    withBackend(backend) {
        selectedLogger().debug("disabled-${mark("disabled-message")}", cause(), fields())
        logger.trace(cause(), fields()) { "disabled-${mark("disabled-lazy-message")}" }
        logger.info("enabled-${mark("eager-message")}", cause(), fields())
        logger.warn(cause(), fields()) { "lazy-${mark("lazy-message")}" }
        logger.error("constant")
        logger.error(message = variableMessage)
    }

    return listOf(
        backend.resolutions.joinToString(",") { it.level.name },
        effects.joinToString(","),
        backend.records.messages.joinToString(","),
    ).joinToString("|")
}
