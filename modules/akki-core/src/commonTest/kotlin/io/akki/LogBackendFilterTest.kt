@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.backend.filter
import io.akki.internal.LogScope
import io.akki.internal.withLogScope
import io.akki.test.RecordingBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogBackendFilterTest {
    @Test
    fun `a filtered backend drops the records its predicate rejects`(): Unit {
        val backend = RecordingBackend()
        val logger = Log.named("filter.level")

        withLogScope(LogScope(backend.filter { _, level -> level >= Level.WARN })) {
            logger.info("dropped")
            logger.warn("kept")
            assertFalse(logger.isEnabled(Level.INFO))
            assertTrue(logger.isEnabled(Level.WARN))
        }

        assertEquals(listOf("kept"), backend.records.map { it.message })
    }

    @Test
    fun `the predicate sees the logger name and reads the current context`(): Unit {
        val backend = RecordingBackend()
        val tenantDebug = backend.filter { name, level ->
            level >= Level.INFO || (name.startsWith("acme.") && LogContext.current()["tenant"] == "acme")
        }

        withLogScope(LogScope(tenantDebug)) {
            Log.named("acme.Checkout").debug("other tenant")
            withLogFields("tenant" to "acme") {
                Log.named("acme.Checkout").debug("acme tenant")
                Log.named("other.Checkout").debug("other logger")
                Log.named("other.Checkout").info("info passes")
            }
        }

        assertEquals(listOf("acme tenant", "info passes"), backend.records.map { it.message })
    }

    @Test
    fun `a filtered backend hands out the sinks of its delegate`(): Unit {
        val sink = Sink { _, _, _ -> }
        val delegate = LogBackend { LoggerBinding { sink } }

        assertSame(sink, delegate.filter { _, _ -> true }.bind("filter.sink").resolve(Level.INFO))
        assertNull(delegate.filter { _, _ -> false }.bind("filter.sink").resolve(Level.INFO))
        assertEquals(
            "Filtered(RecordingBackend(minLevel=TRACE, records=0))",
            RecordingBackend().filter { _, _ -> true }.toString(),
        )
    }
}
