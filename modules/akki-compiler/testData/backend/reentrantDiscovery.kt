// BACKEND_SERVICES: fixture.ReentrantBackend
package fixture

import io.akki.Log
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.test.RecordingBackend
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val recording = RecordingBackend()
private var constructions = 0

class ReentrantBackend : LogBackend {
    init {
        constructions++
        Log.named("bootstrap").info("same thread")
        val background = FutureTask { Log.named("bootstrap").info("background thread") }
        Thread(background, "backend-bootstrap").apply { isDaemon = true; start() }
        background.get(5, SECONDS)
    }

    override fun bind(name: String): LoggerBinding = recording.bind(name)
}

fun box(): String {
    val buffer = ByteArrayOutputStream()
    val original = System.err
    PrintStream(buffer, true).use { captured ->
        System.setErr(captured)
        try {
            val logger = Log.named("bootstrap")
            logger.info("application")
            logger.info("discovered")
        } finally {
            System.setErr(original)
        }
    }
    val output = buffer.toString()
    assertEquals(1, constructions)
    assertEquals(listOf("application", "discovered"), recording.records.map { it.message })
    assertContains(output, "INFO  bootstrap - same thread")
    assertContains(output, "INFO  bootstrap - background thread")
    assertEquals(1, output.lines().count { it.startsWith("akki: backend discovery is in progress") })
    assertFalse(output.contains("no backend found"), output)
    assertFalse(output.contains("broken backend service declaration"), output)
    return "OK"
}
