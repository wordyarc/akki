package io.akki

import io.akki.test.LogRecord
import io.akki.test.recordLogs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogContextTest {
    @Test
    fun `the context is empty outside a block`(): Unit {
        assertSame(LogContext.EMPTY, LogContext.current())
        assertTrue(LogContext.current().fields.isEmpty())
    }

    @Test
    fun `a block installs its context and restores the previous one`(): Unit {
        val context = LogContext("requestId" to 7)

        withLogContext(context) {
            assertEquals(context, LogContext.current())
            withLogContext(LogContext("step" to "validate")) {
                assertEquals(LogContext("step" to "validate"), LogContext.current())
            }
            assertEquals(context, LogContext.current())
        }

        assertSame(LogContext.EMPTY, LogContext.current())
    }

    @Test
    fun `fields are added to the current context`(): Unit {
        withLogFields("requestId" to 7, "tenant" to "acme") {
            withLogFields("step" to "validate", "tenant" to "other") {
                assertEquals(
                    LogContext("requestId" to 7, "tenant" to "other", "step" to "validate"),
                    LogContext.current(),
                )
                assertEquals(listOf("requestId", "tenant", "step"), LogContext.current().fields.keys.toList())
            }
            assertEquals(LogContext("requestId" to 7, "tenant" to "acme"), LogContext.current())
        }
    }

    @Test
    fun `the empty context clears the fields`(): Unit {
        withLogFields("requestId" to 7) {
            withLogContext(LogContext.EMPTY) { assertSame(LogContext.EMPTY, LogContext.current()) }
            assertEquals(LogContext("requestId" to 7), LogContext.current())
        }
    }

    @Test
    fun `the block result is returned`(): Unit {
        assertEquals(42, withLogContext(LogContext("a" to 1)) { 42 })
        assertEquals(42, withLogFields("a" to 1) { 42 })
    }

    @Test
    fun `the context is restored when the block throws`(): Unit {
        assertFailsWith<IllegalStateException> { withLogFields("a" to 1) { error("failed") } }

        assertSame(LogContext.EMPTY, LogContext.current())
    }

    @Test
    fun `plus prefers the right operand and minus removes a key`(): Unit {
        val merged = LogContext("a" to 1, "b" to 2) + LogContext("b" to 3, "c" to null)

        assertEquals(mapOf("a" to 1, "b" to 3, "c" to null), merged.fields)
        assertEquals(3, merged["b"])
        assertNull(merged["c"])
        assertEquals(LogContext("a" to 1, "c" to null), merged - "b")
        assertSame(merged, merged - "missing")
        assertSame(merged, merged + LogContext.EMPTY)
        assertSame(merged, LogContext.EMPTY + merged)
    }

    @Test
    fun `contexts are values`(): Unit {
        val context = LogContext("a" to 1, "b" to null)

        assertEquals(context, LogContext(mapOf("a" to 1, "b" to null)))
        assertEquals(context.hashCode(), LogContext(mapOf("a" to 1, "b" to null)).hashCode())
        assertNotEquals(context, LogContext("a" to 1))
        assertEquals("LogContext(a=1, b=null)", context.toString())
        assertEquals("LogContext()", LogContext.EMPTY.toString())
    }

    @Test
    fun `a context copies the map it is created from`(): Unit {
        val source = mutableMapOf<String, Any?>("a" to 1)
        val context = LogContext(source)

        source["b"] = 2

        assertEquals(mapOf<String, Any?>("a" to 1), context.fields)
    }

    @Test
    fun `a blank key is rejected`(): Unit {
        val failure = assertFailsWith<IllegalArgumentException> { LogContext(" " to 1) }

        assertEquals("akki: a log context key must not be blank", failure.message)
        assertFailsWith<IllegalArgumentException> { LogContext(mapOf("" to 1)) }
    }

    @Test
    fun `entries are closed in reverse order`(): Unit {
        val outer = LogContext("a" to 1).enter()
        val inner = LogContext("b" to 2).enter()

        try {
            val failure = assertFailsWith<IllegalStateException> { outer.close() }
            assertEquals(
                "akki: a logging context must be exited on the thread that entered it, innermost first",
                failure.message,
            )
            assertEquals(LogContext("b" to 2), LogContext.current())
        } finally {
            inner.close()
            outer.close()
        }

        assertSame(LogContext.EMPTY, LogContext.current())
    }

    @Test
    fun `an entry can be closed more than once`(): Unit {
        val entry = LogContext("a" to 1).enter()

        entry.close()
        entry.close()

        assertSame(LogContext.EMPTY, LogContext.current())
    }

    @Test
    fun `records carry the context separately from their fields`(): Unit {
        val logger = Log.named("context.records")

        val records = recordLogs {
            withLogFields("requestId" to 7) { logger.info("scoped", fields = mapOf("orderId" to 42)) }
            logger.info("plain")
        }

        assertEquals(
            LogRecord("context.records", Level.INFO, "scoped", null, mapOf("orderId" to 42), LogContext("requestId" to 7)),
            records[0],
        )
        assertSame(LogContext.EMPTY, records[1].context)
    }

    @Test
    fun `the context does not depend on the scope`(): Unit {
        withLogFields("a" to 1) {
            val records = recordLogs { Log.named("context.scope").info("inside") }

            assertEquals(LogContext("a" to 1), records.single().context)
            assertEquals(LogContext("a" to 1), LogContext.current())
        }
    }
}
