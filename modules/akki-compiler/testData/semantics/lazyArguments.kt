package fixture

import io.akki.*
import io.akki.backend.Sink
import io.akki.test.RecordingLogger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val effects = mutableListOf<String>()

private object InitializedLogger : Logger() {
    init {
        effects += "initialize"
    }

    override val name = "initialized"
    override fun sink(level: Level): Sink? = null
}

private class Message {
    fun text(): String = error("disabled message body")
}

private fun receiver(): Message = Message().also { effects += "bound receiver" }
private fun cause(): Throwable = IllegalStateException().also { effects += "cause" }
private fun fields(): Map<String, Any?> = emptyMap<String, Any?>().also { effects += "fields" }
private fun supplier(): () -> String {
    effects += "supplier"
    return { error("disabled supplier body") }
}

private fun selected(logger: Logger): Logger = logger.also { effects += "logger" }
private fun failingSupplier(): () -> String = throw IllegalArgumentException("supplier expression")

fun box(): String {
    val logger = RecordingLogger(minLevel = Level.INFO)
    selected(logger).debug(message = receiver()::text, fields = fields(), cause = cause())
    selected(logger).debug(cause(), fields(), supplier())
    selected(logger).debug(cause(), fields()) { error("disabled literal body") }
    InitializedLogger.debug { error("disabled object message") }
    assertEquals(
        listOf(
            "logger", "bound receiver", "fields", "cause",
            "logger", "cause", "fields", "supplier",
            "logger", "cause", "fields",
            "initialize",
        ),
        effects,
    )
    assertFailsWith<IllegalArgumentException> {
        logger.debug(message = failingSupplier())
    }
    return "OK"
}
