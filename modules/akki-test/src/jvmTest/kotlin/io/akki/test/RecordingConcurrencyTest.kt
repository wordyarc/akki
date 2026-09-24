package io.akki.test

import io.akki.Log
import io.akki.LogScope
import io.akki.withLogScope
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingConcurrencyTest {
    @Test
    fun `parallel captures see only their own records`() {
        val logger = Log.named("threads.parallel")
        val started = CyclicBarrier(WRITERS)

        val captured = withWriters { executor ->
            List(WRITERS) { writer ->
                executor.submit(
                    Callable {
                        recordLogs {
                            started.await(10, SECONDS)
                            repeat(RECORDS) { logger.info("$writer-$it") }
                        }
                    },
                )
            }.map { it.get(10, SECONDS) }
        }

        captured.forEachIndexed { writer, records ->
            assertEquals(List(RECORDS) { "$writer-$it" }, records.map { it.message })
        }
    }

    @Test
    fun `keeps every record written from several threads`() {
        val backend = RecordingBackend()
        val scope = LogScope(backend)
        val logger = Log.named("threads.shared")

        withWriters { executor ->
            List(WRITERS) { writer ->
                executor.submit { withLogScope(scope) { repeat(RECORDS) { logger.info("$writer-$it") } } }
            }.forEach { it.get(10, SECONDS) }
        }

        assertEquals(WRITERS * RECORDS, backend.records.size)
        assertEquals(WRITERS * RECORDS, backend.records.map { it.message }.toSet().size)
    }

    private fun <T> withWriters(block: (ExecutorService) -> T): T {
        val executor = Executors.newFixedThreadPool(WRITERS) { task ->
            Thread(task, "akki-recording-test").apply { isDaemon = true }
        }
        try {
            return block(executor)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, SECONDS), "recording tasks did not finish")
        }
    }

    private companion object {
        const val WRITERS = 8
        const val RECORDS = 250
    }
}
