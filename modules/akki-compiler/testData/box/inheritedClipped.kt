// WITH_HELPERS
// TREAT_AS_ONE_FILE
// MIN_LEVEL: INFO
// CHECK_BYTECODE_TEXT
// 0 discarded-
// 1 LDC "retained-cause"
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

private fun <T : Forwarding> generic(logger: T) {
    logger.debug("discarded-generic")
}

fun box(): String {
    val recording = RecordingLogger("clipped")
    val concrete = Forwarding(recording)
    val base: Logger = concrete
    val effects = Effects()

    recording.debug("discarded-external")
    concrete.debug("discarded-eager")
    concrete.trace { effects.mark("message", "discarded-lazy") }
    base.debug("discarded-base")
    generic(concrete)
    effects.mark("receiver", concrete).debug(
        fields = effects.mark("fields", mapOf("key" to "retained-field")),
        cause = effects.mark("cause", IllegalStateException("retained-cause")),
        message = effects.mark("message", "retained-named"),
    )
    concrete.info("kept")

    assertEquals(listOf("receiver", "fields", "cause", "message"), effects.toList())
    assertEquals(listOf(LogRecord("clipped", Level.INFO, "kept")), recording.records)
    return "OK"
}
