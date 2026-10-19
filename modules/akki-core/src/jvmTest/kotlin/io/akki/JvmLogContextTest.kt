package io.akki

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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
