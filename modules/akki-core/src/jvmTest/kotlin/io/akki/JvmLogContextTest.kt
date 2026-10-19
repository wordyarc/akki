package io.akki

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmLogContextTest {
    @Test
    fun `a context is confined to the thread that entered it`(): Unit {
        withLogFields("requestId" to 7) {
            var seen: LogContext? = null

            thread { seen = LogContext.current() }.join()

            assertSame(LogContext.EMPTY, seen)
            assertEquals(LogContext("requestId" to 7), LogContext.current())
        }
    }

    @Test
    fun `a context value is re-entered on another thread`(): Unit {
        val captured = withLogFields("requestId" to 7) { LogContext.current() }
        var seen: LogContext? = null

        thread { withLogContext(captured) { seen = LogContext.current() } }.join()

        assertEquals(captured, seen)
    }

    @Test
    fun `wrapped tasks carry the context to another thread and leave it clean`(): Unit {
        val context = LogContext("requestId" to 7)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val inRunnable = AtomicReference<LogContext>()
            executor.submit(context.wrap(Runnable { inRunnable.set(LogContext.current()) })).get(10, SECONDS)
            val inCallable = executor.submit(context.wrap(Callable { LogContext.current() })).get(10, SECONDS)
            val afterwards = executor.submit(Callable { LogContext.current() }).get(10, SECONDS)

            assertEquals(context, inRunnable.get())
            assertEquals(context, inCallable)
            assertSame(LogContext.EMPTY, afterwards)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a wrapped lambda keeps its result`(): Unit {
        val context = LogContext("requestId" to 7)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val submitted: Future<LogContext> = executor.submit(context.wrap { LogContext.current() })
            val executed = AtomicReference<LogContext>()
            executor.execute(context.wrap { executed.set(LogContext.current()) })
            val afterwards = executor.submit(Callable { LogContext.current() }).get(10, SECONDS)
            val task: () -> LogContext = context.wrap { LogContext.current() }

            assertEquals(context, submitted.get(10, SECONDS))
            assertEquals(context, executed.get())
            assertSame(LogContext.EMPTY, afterwards)
            assertEquals(context, task())
            assertSame(LogContext.EMPTY, LogContext.current())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `java sees wrap for runnables and callables only`(): Unit {
        val methods = Class.forName("io.akki.LogContexts").methods.filter { it.name == "wrap" }

        assertEquals(
            setOf(
                listOf(LogContext::class.java, Runnable::class.java),
                listOf(LogContext::class.java, Callable::class.java),
            ),
            methods.filterNot { it.isSynthetic }.map { it.parameterTypes.toList() }.toSet(),
        )
        assertTrue(methods.none { it.isSynthetic })
        val common = Class.forName("io.akki.LogContextKt").methods.filter { it.name == "wrap" }
        assertEquals(listOf(true), common.map { it.isSynthetic && it.parameterTypes.last() == Function0::class.java })
    }

    @Test
    fun `an entry cannot be closed from another thread`(): Unit {
        val entry = LogContext("a" to 1).enter()
        var failure: Throwable? = null

        try {
            thread { runCatching { entry.close() }.onFailure { failure = it } }.join()
        } finally {
            entry.close()
        }

        assertEquals(
            "akki: a logging context must be exited on the thread that entered it, innermost first",
            (failure as IllegalStateException).message,
        )
        assertSame(LogContext.EMPTY, LogContext.current())
    }
}
