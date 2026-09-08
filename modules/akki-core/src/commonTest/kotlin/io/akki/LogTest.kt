package io.akki

import io.akki.test.LogRecord
import io.akki.test.RecordingBackend
import io.akki.test.RecordingLogger
import io.akki.test.Resolution
import io.akki.test.levels
import io.akki.test.messages
import io.akki.test.withBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

        assertEquals(Level.entries, backend.records.levels)
        assertTrue(backend.records.all { it.name == "checkout" })
        assertEquals(listOf("trace", "debug", "info", "warn", "error"), backend.records.messages)
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

        assertEquals(Level.entries, backend.records.levels)
        assertEquals(listOf("trace", "debug", "info", "warn", "error"), backend.records.messages)
        assertTrue(backend.records.all { it.fields == fields })
        assertSame(cause, backend.records.last().cause)
    }

    @Test
    fun `lazy message is evaluated only for an enabled level`(): Unit {
        val backend = RecordingBackend(setOf(Level.ERROR))
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
        assertEquals(listOf("recorded"), backend.records.messages)
        assertEquals(
            listOf(Resolution("lazy-filtered", Level.INFO), Resolution("lazy-filtered", Level.ERROR)),
            backend.resolutions,
        )
    }

    @Test
    fun `backend controls level filtering`(): Unit {
        val backend = RecordingBackend(setOf(Level.ERROR))
        val logger = Log.named("filtered")
        withBackend(backend) {
            assertFalse(logger.isEnabled(Level.INFO))
            assertTrue(logger.isEnabled(Level.ERROR))

            logger.info("ignored")
            logger.error("recorded")
        }

        assertEquals(listOf("recorded"), backend.records.messages)
    }

    @Test
    fun `backend can answer isEnabled without resolving a sink`(): Unit {
        var resolutions = 0
        val backend = object : LogBackend {
            override fun resolve(name: String, level: Level): Sink? {
                resolutions++
                return null
            }

            override fun isEnabled(name: String, level: Level): Boolean = level >= Level.WARN
        }
        val logger = Log.named("fast-enabled")

        withBackend(backend) {
            assertFalse(logger.isEnabled(Level.INFO))
            assertTrue(logger.isEnabled(Level.ERROR))
        }

        assertEquals(0, resolutions)
    }

    @Test
    fun `isEnabled falls back to sink resolution`(): Unit {
        val backend = RecordingBackend(setOf(Level.ERROR))
        val logger = Log.named("fallback-enabled")

        withBackend(backend) {
            assertFalse(logger.isEnabled(Level.INFO))
            assertTrue(logger.isEnabled(Level.ERROR))
        }

        assertEquals(
            listOf(Resolution("fallback-enabled", Level.INFO), Resolution("fallback-enabled", Level.ERROR)),
            backend.resolutions,
        )
    }

    @Test
    fun `uninstall restores the previous backend`(): Unit {
        val logger = Log.named("replaceable")
        val first = RecordingBackend()
        val second = RecordingBackend()

        withBackend(first) {
            logger.info("first")
            withBackend(second) { logger.info("second") }
            logger.info("restored")
        }

        assertEquals(listOf("first", "restored"), first.records.messages)
        assertEquals(listOf("second"), second.records.messages)
    }

    @Test
    fun `stale installation cannot replace a newer backend`(): Unit {
        val logger = Log.named("ordered")
        val first = RecordingBackend()
        val second = RecordingBackend()
        val firstInstallation = Log.install(first)
        val secondInstallation = Log.install(second)

        try {
            assertFailsWith<IllegalStateException> { firstInstallation.uninstall() }
            logger.info("current")
        } finally {
            secondInstallation.uninstall()
            firstInstallation.uninstall()
        }

        assertTrue(first.records.isEmpty())
        assertEquals(listOf("current"), second.records.messages)
    }

    @Test
    fun `installation can be uninstalled more than once`(): Unit {
        val installation = Log.install(RecordingBackend())

        installation.uninstall()
        installation.uninstall()
    }

    @Test
    fun `repeated installation of the same backend has an independent lifecycle`(): Unit {
        val backend = RecordingBackend()
        val first = Log.install(backend)
        val second = Log.install(backend)

        try {
            assertFailsWith<IllegalStateException> { first.uninstall() }
        } finally {
            second.uninstall()
            first.uninstall()
        }
    }

    @Test
    fun `installation is uninstalled on close`(): Unit {
        val outer = RecordingBackend()
        val inner = RecordingBackend()
        val logger = Log.named("closeable")

        withBackend(outer) {
            Log.install(inner).use { logger.info("inside") }
            logger.info("outside")
        }

        assertEquals(listOf("inside"), inner.records.messages)
        assertEquals(listOf("outside"), outer.records.messages)
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
