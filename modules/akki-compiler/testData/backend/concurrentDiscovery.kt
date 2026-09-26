// BACKEND_SERVICES: fixture.SlowBackend
package fixture

import io.akki.Log
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.test.RecordingBackend
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import kotlin.test.assertEquals

private val recording = RecordingBackend()
private val discovering = CountDownLatch(1)
private lateinit var worker: Thread

class SlowBackend : LogBackend {
    init {
        discovering.countDown()
        val deadline = System.nanoTime() + SECONDS.toNanos(5)
        while (worker.state != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.sleep(1)
    }

    override fun bind(name: String): LoggerBinding = recording.bind(name)
}

fun box(): String {
    val buffer = ByteArrayOutputStream()
    val original = System.err
    PrintStream(buffer, true).use { captured ->
        System.setErr(captured)
        try {
            worker = thread {
                discovering.await()
                Log.named("worker").info("concurrent")
                Log.named("worker").debug("concurrent debug")
            }
            Log.named("main").info("discovering")
            worker.join()
        } finally {
            System.setErr(original)
        }
    }
    assertEquals(setOf("discovering", "concurrent", "concurrent debug"), recording.records.map { it.message }.toSet())
    assertEquals("", buffer.toString())
    return "OK"
}
