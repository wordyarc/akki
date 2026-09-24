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
    var sequence = 0

    fun numbered(effect: String): Int = effects.mark(effect, ++sequence)

    fun cause(): Throwable = IllegalStateException("cause-${numbered("cause")}")
    fun fields(): Map<String, Any?> = mapOf("value" to numbered("fields"))
    val variableMessage: () -> String = { "variable-${numbered("variable-message")}" }

    LogScope(backend).run {
        effects.mark("receiver", logger).debug("disabled-${numbered("disabled-message")}", cause(), fields())
        logger.trace(cause(), fields()) { "disabled-${numbered("disabled-lazy-message")}" }
        logger.info("enabled-${numbered("eager-message")}", cause(), fields())
        logger.warn(cause(), fields()) { "lazy-${numbered("lazy-message")}" }
        logger.error("constant")
        logger.error(message = variableMessage)
    }

    assertEquals("DEBUG,TRACE,INFO,WARN,ERROR,ERROR", backend.resolutions.joinToString(",") { it.level.name })
    assertEquals(
        "receiver,disabled-message,cause,fields,cause,fields,eager-message,cause,fields,cause,fields,lazy-message,variable-message",
        effects.toString(),
    )
    assertEquals("enabled-6,lazy-11,constant,variable-12", backend.records.joinToString(",") { it.message })
    return "OK"
}
