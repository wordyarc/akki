@file:OptIn(io.akki.DelicateAkkiApi::class)

package fixture

import io.akki.*
import io.akki.test.RecordingBackend
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Owner : Runnable {
    override fun run() {
        log.info { "worker" }
    }

    companion object {
        val worker: Thread = Thread(Owner()).apply {
            start()
            join(SECONDS.toMillis(5))
        }
    }
}

fun box(): String {
    val recording = RecordingBackend()
    Log.install(recording).use {
        assertFalse(Owner.worker.isAlive, "the worker waited for the static initializer of its logger owner")
    }
    assertEquals(listOf("worker"), recording.records.map { it.message })
    return "OK"
}
