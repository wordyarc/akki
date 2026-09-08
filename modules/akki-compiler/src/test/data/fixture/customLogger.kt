package fixture

import io.akki.*
import io.akki.internal.*

@OptIn(InternalAkkiApi::class)
private class Probe(override val name: String) : Logger() {
    val calls: MutableList<String> = mutableListOf()

    override fun isEnabled(level: Level): Boolean = level != Level.DEBUG

    override fun emit(level: Level, message: String, cause: Throwable?, fields: Map<String, Any?>) {
        calls += "emit:$level:$message"
    }

    override fun sink(level: Level): Sink? {
        calls += "sink:$level"
        return if (isEnabled(level)) Sink { message, _, _ -> calls += "own:$level:$message" } else null
    }
}

private class Prefixing(private val delegate: Logger) : Logger() {
    override val name: String get() = delegate.name

    override fun isEnabled(level: Level): Boolean = delegate.isEnabled(level)

    override fun emit(level: Level, message: String, cause: Throwable?, fields: Map<String, Any?>) {
        delegate.emit(level, "[$name] $message", cause, fields)
    }
}

fun box(): String {
    val probe = Probe("probe")
    probe.info("kept")
    probe.debug("dropped")
    probe.warn { "lazy" }

    val recording = Probe("delegate")
    Prefixing(recording).error("decorated")

    return (probe.calls + recording.calls).joinToString(",")
}
