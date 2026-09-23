package io.akki

import io.akki.test.RecordingBackend
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(DelicateAkkiApi::class)
class JvmLogScopeTest {
    @Test
    fun `a scope is confined to the thread that entered it`(): Unit {
        val scoped = RecordingBackend()
        val installed = RecordingBackend()
        val logger = Log.named("scope.thread")

        Log.install(installed).use {
            LogScope(scoped).run {
                thread {
                    assertNull(LogScope.current())
                    logger.info("worker")
                }.join()
                logger.info("owner")
            }
        }

        assertEquals(listOf("owner"), scoped.records.map { it.message })
        assertEquals(listOf("worker"), installed.records.map { it.message })
    }

    @Test
    fun `an entry cannot be closed from another thread`(): Unit {
        val entry = LogScope(RecordingBackend()).enter()
        var failure: Throwable? = null

        try {
            thread { runCatching { entry.close() }.onFailure { failure = it } }.join()
        } finally {
            entry.close()
        }

        assertEquals(
            "akki: logging scopes must be exited on their own thread in reverse order",
            (failure as IllegalStateException).message,
        )
        assertNull(LogScope.current())
    }
}
