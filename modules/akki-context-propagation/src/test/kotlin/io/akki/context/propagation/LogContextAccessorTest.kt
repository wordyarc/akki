package io.akki.context.propagation

import io.akki.LogContext
import io.akki.withLogContext
import io.akki.withLogFields
import io.micrometer.context.ContextRegistry
import io.micrometer.context.ContextSnapshotFactory
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class LogContextAccessorTest {
    private val snapshots = ContextSnapshotFactory.builder().build()

    @Test
    fun `the accessor is registered through the service loader`() {
        assertTrue(ContextRegistry.getInstance().threadLocalAccessors.any { it is LogContextAccessor })
    }

    @Test
    fun `a snapshot carries the context to another thread and leaves it clean`() {
        val snapshot = withLogFields("requestId" to 7) { snapshots.captureAll() }
        val executor = Executors.newSingleThreadExecutor()

        try {
            val seen = executor.submit(snapshot.wrap(Callable { LogContext.current() })).get(10, SECONDS)
            val afterwards = executor.submit(Callable { LogContext.current() }).get(10, SECONDS)

            assertEquals(LogContext("requestId" to 7), seen)
            assertSame(LogContext.EMPTY, afterwards)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `restoring a snapshot gives the thread its own entry back`() {
        val entry = LogContext("a" to 1).enter()

        try {
            val other = withLogContext(LogContext("b" to 2)) { snapshots.captureAll() }
            other.setThreadLocals().use { assertEquals(LogContext("b" to 2), LogContext.current()) }
            assertEquals(LogContext("a" to 1), LogContext.current())
        } finally {
            entry.close()
        }

        assertSame(LogContext.EMPTY, LogContext.current())
    }

    @Test
    fun `a snapshot without a context clears the target only when asked to`() {
        val keeping = snapshots.captureAll()
        val clearing = ContextSnapshotFactory.builder().clearMissing(true).build().captureAll()

        withLogFields("a" to 1) {
            keeping.setThreadLocals().use { assertEquals(LogContext("a" to 1), LogContext.current()) }
            clearing.setThreadLocals().use { assertSame(LogContext.EMPTY, LogContext.current()) }
            assertEquals(LogContext("a" to 1), LogContext.current())
        }
    }

    @Test
    fun `reactor restores the context written to its own context`() {
        Hooks.enableAutomaticContextPropagation()
        try {
            val seen = Mono.just("record")
                .subscribeOn(Schedulers.boundedElastic())
                .handle<LogContext> { _, sink -> sink.next(LogContext.current()) }
                .contextWrite { it.put(LogContextAccessor.KEY, LogContext("requestId" to 7)) }
                .block(Duration.ofSeconds(10))

            assertEquals(LogContext("requestId" to 7), seen)
            assertSame(LogContext.EMPTY, LogContext.current())
        } finally {
            Hooks.disableAutomaticContextPropagation()
        }
    }

    @Test
    fun `the accessor names its key`() {
        assertEquals("io.akki.LogContext", LogContextAccessor.KEY)
        assertEquals("LogContextAccessor(io.akki.LogContext)", LogContextAccessor().toString())
    }
}
