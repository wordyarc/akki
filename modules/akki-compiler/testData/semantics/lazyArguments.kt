// WITH_HELPERS
package fixture

import helpers.Effects
import io.akki.*
import io.akki.backend.Sink
import io.akki.test.RecordingLogger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val effects = Effects()

private object InitializedLogger : Logger() {
    init {
        effects.mark("initialize")
    }

    override val name = "initialized"
    override fun sink(level: Level): Sink? = null
}

private class Message {
    fun text(): String = error("disabled message body")
}

private fun receiver(): Message = effects.mark("bound receiver", Message())
private fun cause(): Throwable = effects.mark("cause", IllegalStateException())
private fun fields(): Map<String, Any?> = effects.mark("fields", emptyMap())
private fun supplier(): () -> String = effects.mark("supplier") { error("disabled supplier body") }

private fun selected(logger: Logger): Logger = effects.mark("logger", logger)
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
        effects.toList(),
    )
    assertFailsWith<IllegalArgumentException> {
        logger.debug(message = failingSupplier())
    }
    return "OK"
}
