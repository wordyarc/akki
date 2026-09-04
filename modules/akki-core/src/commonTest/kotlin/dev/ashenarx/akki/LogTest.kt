package dev.ashenarx.akki

import dev.ashenarx.akki.test.LogRecord
import dev.ashenarx.akki.test.RecordingBackend
import dev.ashenarx.akki.test.RecordingLogger
import dev.ashenarx.akki.test.Resolution
import dev.ashenarx.akki.test.levels
import dev.ashenarx.akki.test.messages
import dev.ashenarx.akki.test.withBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(DelicateAkkiApi::class)
class LogTest {
    @Test
    fun factoriesResolveTheSameInstance(): Unit {
        assertSame(Log.of<Sample>(), Log.of(Sample::class))
        assertSame(Log.named("sample"), Log.named("sample"))
    }

    @Test
    fun namedLogExposesItsName(): Unit {
        val logger = Log.named("audit")

        assertEquals("audit", logger.name)
        assertEquals("Logger(audit)", logger.toString())
    }

    @Test
    fun loggerCanBeImplementedByConsumers(): Unit {
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
    fun levelMethodsEmitThroughTheInstalledBackend(): Unit {
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
    fun lazyLevelMethodsEmitThroughTheInstalledBackend(): Unit {
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
    fun lazyMessageIsEvaluatedOnlyForEnabledLevel(): Unit {
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
    fun backendControlsLevelFiltering(): Unit {
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
    fun backendCanAnswerIsEnabledWithoutResolvingSink(): Unit {
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
    fun isEnabledFallsBackToSinkResolution(): Unit {
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
    fun uninstallRestoresPreviousBackend(): Unit {
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
    fun staleInstallationCannotReplaceNewerBackend(): Unit {
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
    fun installationCanBeUninstalledMoreThanOnce(): Unit {
        val installation = Log.install(RecordingBackend())

        installation.uninstall()
        installation.uninstall()
    }

    @Test
    fun repeatedInstallationOfSameBackendHasIndependentLifecycle(): Unit {
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
    fun installationIsUninstalledOnClose(): Unit {
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
    fun causeIsForwardedToSink(): Unit {
        val backend = RecordingBackend()
        val cause = IllegalStateException("failed")
        withBackend(backend) {
            Log.named("failure").error("operation failed", cause)
        }

        assertSame(cause, backend.records.single().cause)
    }

    private class Sample
}
