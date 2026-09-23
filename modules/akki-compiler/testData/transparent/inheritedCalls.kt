package fixture

import io.akki.*
import io.akki.backend.Sink
import io.akki.test.*
import kotlin.test.assertEquals

private open class Forwarding(private val delegate: Logger) : Logger() {
    override val name: String get() = delegate.name
    override fun sink(level: Level): Sink? = delegate.sink(level)
}

private class Derived(delegate: Logger) : Forwarding(delegate)

private val disabledEffects = mutableListOf<String>()

private fun message(): String = "disabled".also { disabledEffects += "message" }
private fun cause(): Throwable = IllegalStateException().also { disabledEffects += "cause" }
private fun fields(): Map<String, Any?> = emptyMap<String, Any?>().also { disabledEffects += "fields" }

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
        disabledEffects,
    )

    val failure = IllegalStateException("cause")
    val data = mapOf("id" to 42)
    val effects = mutableListOf<String>()
    derived.info(
        fields = data.also { effects += "fields" },
        message = "eager".also { effects += "message" },
        cause = failure.also { effects += "cause" },
    )
    concrete.warn(
        failure.also { effects += "lazy-cause" },
        data.also { effects += "lazy-fields" },
    ) { effects += "lazy-message"; "lazy" }

    assertEquals(listOf("fields", "message", "cause", "lazy-cause", "lazy-fields", "lazy-message"), effects)
    assertEquals(
        listOf(
            LogRecord("inherited", Level.INFO, "eager", failure, data),
            LogRecord("inherited", Level.WARN, "lazy", failure, data),
        ),
        recording.records,
    )
    return "OK"
}
