package fixture

import io.akki.*
import io.akki.backend.*
import kotlin.test.assertEquals

private class Probe(override val name: String) : Logger() {
    val calls: MutableList<String> = mutableListOf()

    override fun sink(level: Level): Sink? {
        calls += "sink:$level"
        return if (level != Level.DEBUG) Sink { message, _, _ -> calls += "own:$level:$message" } else null
    }
}

private class Prefixing(private val delegate: Logger) : Logger() {
    override val name: String get() = delegate.name

    override fun sink(level: Level): Sink? = delegate.sink(level)?.let { inner ->
        Sink { message, cause, fields -> inner.emit("[$name] $message", cause, fields) }
    }
}

fun box(): String {
    val probe = Probe("probe")
    probe.info("kept")
    probe.debug("dropped")
    probe.warn { "lazy" }

    val recording = Probe("delegate")
    Prefixing(recording).error("decorated")

    assertEquals(
        "sink:INFO,own:INFO:kept,sink:DEBUG,sink:WARN,own:WARN:lazy,sink:ERROR,own:ERROR:[delegate] decorated",
        (probe.calls + recording.calls).joinToString(","),
    )
    return "OK"
}
