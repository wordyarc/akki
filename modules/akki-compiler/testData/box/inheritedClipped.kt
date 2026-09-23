// MIN_LEVEL: INFO
// CHECK_BYTECODE_TEXT
// 0 discarded-
package fixture

import io.akki.*
import io.akki.backend.Sink
import io.akki.test.*
import kotlin.test.assertEquals

private open class Forwarding(private val delegate: Logger) : Logger() {
    override val name: String get() = delegate.name
    override fun sink(level: Level): Sink? = delegate.sink(level)
}

private fun <T : Logger> selected(logger: T, effects: MutableList<String>): T {
    effects += "receiver"
    return logger
}

private fun <T : Forwarding> generic(logger: T) {
    logger.debug("discarded-generic")
}

fun box(): String {
    val recording = RecordingLogger("clipped")
    val concrete = Forwarding(recording)
    val base: Logger = concrete
    val effects = mutableListOf<String>()

    recording.debug("discarded-external")
    concrete.debug("discarded-eager")
    concrete.trace { effects += "message"; "discarded-lazy" }
    base.debug("discarded-base")
    generic(concrete)
    selected(concrete, effects).debug(
        fields = mapOf("key" to "discarded-field").also { effects += "fields" },
        cause = IllegalStateException("discarded-cause").also { effects += "cause" },
        message = "discarded-named".also { effects += "message" },
    )
    concrete.info("kept")

    assertEquals(listOf("receiver"), effects)
    assertEquals(listOf(LogRecord("clipped", Level.INFO, "kept")), recording.records)
    return "OK"
}
