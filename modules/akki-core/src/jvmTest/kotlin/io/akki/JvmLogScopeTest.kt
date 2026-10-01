package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.test.RecordingBackend
import java.lang.management.ManagementFactory
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                    assertNull(LogScope.currentOrNull())
                    logger.info("worker")
                }.join()
                logger.info("owner")
            }
        }

        assertEquals(listOf("owner"), scoped.records.map { it.message })
        assertEquals(listOf("worker"), installed.records.map { it.message })
    }

    @Test
    fun `a scope binds many loggers without copying its bindings`(): Unit {
        val loggers = List(20_000) { Log.named("scope.many.$it") }
        val scope = LogScope(LogBackend { LoggerBinding { null } })

        val allocated = allocatedBytes { scope.run { loggers.forEach { it.info("dropped") } } }

        assertTrue(allocated < 64L * 1024 * 1024, "allocated $allocated bytes")
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
        assertNull(LogScope.currentOrNull())
    }

    @Test
    fun `an entry cannot be closed from another thread in the same scope`(): Unit {
        val scope = LogScope(RecordingBackend())
        val entry = scope.enter()
        var failure: Throwable? = null
        var workerScope: LogScope? = scope

        try {
            thread {
                scope.enter().use { runCatching { entry.close() }.onFailure { failure = it } }
                workerScope = LogScope.currentOrNull()
            }.join()
        } finally {
            entry.close()
        }

        assertEquals(
            "akki: logging scopes must be exited on their own thread in reverse order",
            (failure as IllegalStateException).message,
        )
        assertNull(workerScope)
        assertNull(LogScope.currentOrNull())
    }

    private fun allocatedBytes(block: () -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        block()
        return threads.currentThreadAllocatedBytes - before
    }
}
