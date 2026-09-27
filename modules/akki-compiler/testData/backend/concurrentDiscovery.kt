// BACKEND_SERVICES: fixture.SlowBackend
package fixture

import io.akki.Log
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.test.RecordingBackend
import io.akki.testing.BackgroundTask
import io.akki.testing.awaitSignal
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val recording = RecordingBackend()
private val discovering = CountDownLatch(1)
private val logging = CountDownLatch(1)
private lateinit var worker: BackgroundTask<Unit>

class SlowBackend : LogBackend {
    init {
        discovering.countDown()
        logging.awaitSignal("the worker did not reach the logging call")
        val deadline = System.nanoTime() + SECONDS.toNanos(5)
        while (worker.state != Thread.State.TIMED_WAITING) {
            assertTrue(System.nanoTime() < deadline, "the worker did not wait for discovery: ${worker.state}")
            Thread.sleep(1)
        }
    }

    override fun bind(name: String): LoggerBinding = recording.bind(name)
}

fun box(): String {
    val buffer = ByteArrayOutputStream()
    val original = System.err
    PrintStream(buffer, true).use { captured ->
        System.setErr(captured)
        try {
            BackgroundTask("concurrent-logging") {
                discovering.awaitSignal("backend discovery did not start")
                logging.countDown()
                Log.named("worker").info("concurrent")
                Log.named("worker").debug("concurrent debug")
            }.use { background ->
                worker = background
                BackgroundTask("backend-discovery") {
                    Log.named("main").info("discovering")
                }.use { it.await() }
                background.await()
            }
        } finally {
            System.setErr(original)
        }
    }
    assertEquals(setOf("discovering", "concurrent", "concurrent debug"), recording.records.map { it.message }.toSet())
    assertEquals("", buffer.toString())
    return "OK"
}
