package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend(Level.INFO)
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

    assertEquals("DEBUG,TRACE,INFO,WARN,ERROR,ERROR", backend.resolutions.joinToString(",") { it.level.name })
    assertEquals(
        "receiver,eager-message,cause,fields,cause,fields,lazy-message,variable-message",
        effects.joinToString(","),
    )
    assertEquals("enabled-1,lazy-6,constant,variable-7", backend.records.joinToString(",") { it.message })
    return "OK"
}
