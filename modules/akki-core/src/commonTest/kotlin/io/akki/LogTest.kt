package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.test.LogRecord
import io.akki.test.RecordingBackend
import io.akki.test.RecordingLogger
import io.akki.test.Resolution
import io.akki.test.withBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(DelicateAkkiApi::class)
class LogTest {
    @Test
    fun `factories resolve the same instance`(): Unit {
        assertSame(Log.of<Sample>(), Log.of(Sample::class))
        assertSame(Log.named("sample"), Log.named("sample"))
    }

    @Test
    fun `named log exposes its name`(): Unit {
        val logger = Log.named("audit")

        assertEquals("audit", logger.name)
        assertEquals("Logger(audit)", logger.toString())
    }

    @Test
    fun `a consumer Logger reuses one sink per level`(): Unit {
        val logger = RecordingLogger("custom", minLevel = Level.INFO)

        assertSame(logger.sink(Level.INFO), logger.sink(Level.INFO))
        assertNull(logger.sink(Level.DEBUG))
    }

    @Test
    fun `Logger can be implemented by consumers`(): Unit {
        val logger = RecordingLogger("custom")
        val fields = mapOf("source" to "test")

        logger.info("implemented", fields = fields)
        logger.info(fields = fields) { "implemented lazily" }

        assertEquals(
            listOf(
                LogRecord("custom", Level.INFO, "implemented", fields = fields),
                LogRecord("custom", Level.INFO, "implemented lazily", fields = fields),
            ),
            logger.records,
        )
    }

    @Test
    fun `level methods emit through the installed backend`(): Unit {
        val backend = RecordingBackend()
        val logger = Log.named("checkout")
        val fields = mapOf("orderId" to 42)
        withBackend(backend) {
            logger.trace("trace", fields = fields)
            logger.debug("debug", fields = fields)
            logger.info("info", fields = fields)
            logger.warn("warn", fields = fields)
            logger.error("error", fields = fields)
        }

        assertEquals(Level.entries, backend.records.map { it.level })
        assertTrue(backend.records.all { it.name == "checkout" })
        assertEquals(listOf("trace", "debug", "info", "warn", "error"), backend.records.map { it.message })
        assertTrue(backend.records.all { it.fields == fields })
    }

    @Test
    fun `lazy level methods emit through the installed backend`(): Unit {
        val backend = RecordingBackend()
        val logger = Log.named("lazy-checkout")
        val fields = mapOf("orderId" to 42)
        val cause = IllegalStateException("failed")
        withBackend(backend) {
            logger.trace(fields = fields) { "trace" }
            logger.debug(fields = fields) { "debug" }
            logger.info(fields = fields) { "info" }
            logger.warn(fields = fields) { "warn" }
            logger.error(cause, fields) { "error" }
        }

        assertEquals(Level.entries, backend.records.map { it.level })
        assertEquals(listOf("trace", "debug", "info", "warn", "error"), backend.records.map { it.message })
        assertTrue(backend.records.all { it.fields == fields })
        assertSame(cause, backend.records.last().cause)
    }

    @Test
    fun `lazy message is evaluated only for an enabled level`(): Unit {
        val backend = RecordingBackend(Level.ERROR)
        val logger = Log.named("lazy-filtered")
        val evaluatedLevels: MutableList<Level> = mutableListOf()
        withBackend(backend) {
            logger.info {
                evaluatedLevels += Level.INFO
                "ignored"
            }
            logger.error {
                evaluatedLevels += Level.ERROR
                "recorded"
            }
        }

        assertEquals(listOf(Level.ERROR), evaluatedLevels)
        assertEquals(listOf("recorded"), backend.records.map { it.message })
        assertEquals(
            listOf(Resolution("lazy-filtered", Level.INFO), Resolution("lazy-filtered", Level.ERROR)),
            backend.resolutions,
        )
    }

    @Test
    fun `backend controls level filtering`(): Unit {
        val backend = RecordingBackend(Level.ERROR)
        val logger = Log.named("filtered")
        withBackend(backend) {
            assertFalse(logger.isEnabled(Level.INFO))
            assertTrue(logger.isEnabled(Level.ERROR))

            logger.info("ignored")
            logger.error("recorded")
        }

        assertEquals(listOf("recorded"), backend.records.map { it.message })
    }

    @Test
    fun `a backend is bound once and asked per record`(): Unit {
        var binds = 0
        val backend = LogBackend { name ->
            binds++
            LoggerBinding { level -> if (level >= Level.WARN) Sink { _, _, _ -> } else null }
        }
        val logger = Log.named("bound-once")

        withBackend(backend) {
            assertFalse(logger.isEnabled(Level.INFO))
            assertTrue(logger.isEnabled(Level.ERROR))
            logger.error("recorded")
        }

        assertEquals(1, binds)
    }

    @Test
    fun `isEnabled answers from the same binding as sink`(): Unit {
        val backend = RecordingBackend(Level.ERROR)
        val logger = Log.named("consistent-enabled")

        withBackend(backend) {
            assertFalse(logger.isEnabled(Level.INFO))
            assertTrue(logger.isEnabled(Level.ERROR))
        }

        assertEquals(
            listOf(Resolution("consistent-enabled", Level.INFO), Resolution("consistent-enabled", Level.ERROR)),
            backend.resolutions,
        )
    }

    @Test
    fun `close restores the previous backend`(): Unit {
        val logger = Log.named("replaceable")
        val first = RecordingBackend()
        val second = RecordingBackend()

        withBackend(first) {
            logger.info("first")
            withBackend(second) { logger.info("second") }
            logger.info("restored")
        }

        assertEquals(listOf("first", "restored"), first.records.map { it.message })
        assertEquals(listOf("second"), second.records.map { it.message })
    }

    @Test
    fun `stale installation cannot replace a newer backend`(): Unit {
        val logger = Log.named("ordered")
        val first = RecordingBackend()
        val second = RecordingBackend()
        val firstInstallation = Log.install(first)
        val secondInstallation = Log.install(second)

        try {
            assertFailsWith<IllegalStateException> { firstInstallation.close() }
            logger.info("current")
        } finally {
            secondInstallation.close()
            firstInstallation.close()
        }

        assertTrue(first.records.isEmpty())
        assertEquals(listOf("current"), second.records.map { it.message })
    }

    @Test
    fun `installation can be closed more than once`(): Unit {
        val installation = Log.install(RecordingBackend())

        installation.close()
        installation.close()
    }

    @Test
    fun `repeated installation of the same backend has an independent lifecycle`(): Unit {
        val backend = RecordingBackend()
        val first = Log.install(backend)
        val second = Log.install(backend)

        try {
            assertFailsWith<IllegalStateException> { first.close() }
        } finally {
            second.close()
            first.close()
        }
    }

    @Test
    fun `installation is released on close`(): Unit {
        val outer = RecordingBackend()
        val inner = RecordingBackend()
        val logger = Log.named("closeable")

        withBackend(outer) {
            Log.install(inner).use { logger.info("inside") }
            logger.info("outside")
        }

        assertEquals(listOf("inside"), inner.records.map { it.message })
        assertEquals(listOf("outside"), outer.records.map { it.message })
    }

    @Test
    fun `cause is forwarded to the sink`(): Unit {
        val backend = RecordingBackend()
        val cause = IllegalStateException("failed")
        withBackend(backend) {
            Log.named("failure").error("operation failed", cause)
        }

        assertSame(cause, backend.records.single().cause)
    }

    private class Sample
}
