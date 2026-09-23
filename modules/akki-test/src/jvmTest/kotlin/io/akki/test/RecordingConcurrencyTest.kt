package io.akki.test

import io.akki.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingConcurrencyTest {
    @Test
    fun `keeps every record written from several threads`() {
        val backend = RecordingBackend()
        val logger = Log.named("threads")

        withBackend(backend) {
            val executor = Executors.newFixedThreadPool(4) { task ->
                Thread(task, "akki-recording-test").apply { isDaemon = true }
            }
            try {
                List(4) { writer -> executor.submit { repeat(250) { logger.info("$writer-$it") } } }
                    .forEach { it.get(10, SECONDS) }
            } finally {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(10, SECONDS), "recording tasks did not finish")
            }
        }

        assertEquals(1000, backend.records.size)
        assertEquals(1000, backend.records.map { it.message }.toSet().size)
    }
}
