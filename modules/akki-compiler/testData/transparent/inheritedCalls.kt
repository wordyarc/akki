// WITH_HELPERS
package fixture

import helpers.Effects
import io.akki.*
import io.akki.backend.Sink
import io.akki.test.*
import kotlin.test.assertEquals

private open class Forwarding(private val delegate: Logger) : Logger() {
    override val name: String get() = delegate.name
    override fun sink(level: Level): Sink? = delegate.sink(level)
}

private class Derived(delegate: Logger) : Forwarding(delegate)

private val disabledEffects = Effects()

private fun message(): String = disabledEffects.mark("message", "disabled")
private fun cause(): Throwable = disabledEffects.mark("cause", IllegalStateException())
private fun fields(): Map<String, Any?> = disabledEffects.mark("fields", emptyMap())

private fun <T : Forwarding> generic(logger: T) {
    logger.debug(message(), cause(), fields())
}

fun box(): String {
    val recording = RecordingLogger("inherited", Level.INFO)
    val concrete = Forwarding(recording)
    val derived = Derived(recording)
    val base: Logger = concrete
    val anonymous = object : Forwarding(recording) {}

    recording.debug(message(), cause(), fields())
    concrete.debug(message(), cause(), fields())
    concrete.debug(cause(), fields()) { message() }
    derived.debug(fields = fields(), message = message(), cause = cause())
    base.debug(message(), cause(), fields())
    anonymous.debug(message(), cause(), fields())
    generic(derived)

    assertEquals(
        listOf(
            "message", "cause", "fields",
            "message", "cause", "fields",
            "cause", "fields",
            "fields", "message", "cause",
            "message", "cause", "fields",
            "message", "cause", "fields",
            "message", "cause", "fields",
        ),
        disabledEffects.toList(),
    )

    val failure = IllegalStateException("cause")
    val data = mapOf("id" to 42)
    val effects = Effects()
    derived.info(
        fields = effects.mark("fields", data),
        message = effects.mark("message", "eager"),
        cause = effects.mark("cause", failure),
    )
    concrete.warn(
        effects.mark("lazy-cause", failure),
        effects.mark("lazy-fields", data),
    ) { effects.mark("lazy-message", "lazy") }

    assertEquals(listOf("fields", "message", "cause", "lazy-cause", "lazy-fields", "lazy-message"), effects.toList())
    assertEquals(
        listOf(
            LogRecord("inherited", Level.INFO, "eager", failure, data),
            LogRecord("inherited", Level.WARN, "lazy", failure, data),
        ),
        recording.records,
    )
    return "OK"
}
