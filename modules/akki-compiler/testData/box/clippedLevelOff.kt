// WITH_HELPERS
// MIN_LEVEL: OFF
@file:OptIn(io.akki.InternalAkkiApi::class)

package fixture

import helpers.Effects
import io.akki.*
import io.akki.internal.LogScope
import io.akki.internal.withLogScope
import io.akki.test.*
import kotlin.test.assertEquals

fun box(): String {
    val backend = RecordingBackend()
    val logger = Log.named("fixture")
    val effects = Effects()
    val dynamic = Level.entries.first()

    val gate = withLogScope(LogScope(backend)) {
        logger.trace("trace-${effects.mark("trace")}")
        logger.debug { "debug-${effects.mark("debug-lazy")}" }
        effects.mark("receiver", logger).debug("receiver-${effects.mark("receiver-message")}")
        logger.info("info-${effects.mark("info")}")
        logger.error("error-${effects.mark("error")}")
        "enabled=${effects.mark("enabled-receiver", logger).isEnabled(Level.TRACE)}," +
            "info=${logger.isEnabled(Level.INFO)},dynamic=${logger.isEnabled(dynamic)}," +
            "sink=${logger.sink(Level.DEBUG) != null}"
    }

    assertEquals("", backend.records.joinToString(",") { it.message })
    assertEquals("trace,receiver,receiver-message,info,error,enabled-receiver", effects.toString())
    assertEquals("enabled=false,info=false,dynamic=true,sink=true", gate)
    return "OK"
}
