package dev.ashenarx.akki

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
        val logger = RecordingLogger()
        val fields = mapOf("source" to "test")

        logger.info("implemented", fields = fields)
        logger.info(fields = fields) { "implemented lazily" }

        assertEquals(
            listOf(
                Event("custom", Level.INFO, "implemented", fields),
                Event("custom", Level.INFO, "implemented lazily", fields),
            ),
            logger.events,
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

        assertEquals(Level.entries, backend.events.map(Event::level))
        assertTrue(backend.events.all { it.name == "checkout" })
        assertEquals(listOf("trace", "debug", "info", "warn", "error"), backend.events.map(Event::message))
        assertTrue(backend.events.all { it.fields == fields })
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

        assertEquals(Level.entries, backend.events.map(Event::level))
        assertEquals(listOf("trace", "debug", "info", "warn", "error"), backend.events.map(Event::message))
        assertTrue(backend.events.all { it.fields == fields })
        assertSame(cause, backend.events.last().cause)
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
        assertEquals(listOf("recorded"), backend.events.map(Event::message))
        assertEquals(
            listOf("lazy-filtered" to Level.INFO, "lazy-filtered" to Level.ERROR),
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

        assertEquals(listOf("recorded"), backend.events.map(Event::message))
    }

    @Test
    fun uninstallRestoresPreviousBackend(): Unit {
        val logger = Log.named("replaceable")
        val first = RecordingBackend()
        val second = RecordingBackend()
        val firstInstallation = Log.install(first)

        try {
            logger.info("first")
            val secondInstallation = Log.install(second)
            try {
                logger.info("second")
            } finally {
                secondInstallation.uninstall()
            }
            logger.info("restored")
        } finally {
            firstInstallation.uninstall()
        }

        assertEquals(listOf("first", "restored"), first.events.map(Event::message))
        assertEquals(listOf("second"), second.events.map(Event::message))
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

        assertTrue(first.events.isEmpty())
        assertEquals(listOf("current"), second.events.map(Event::message))
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

        assertEquals(listOf("inside"), inner.events.map(Event::message))
        assertEquals(listOf("outside"), outer.events.map(Event::message))
    }

    @Test
    fun causeIsForwardedToSink(): Unit {
        val backend = RecordingBackend()
        val cause = IllegalStateException("failed")
        withBackend(backend) {
            Log.named("failure").error("operation failed", cause)
        }

        assertSame(cause, backend.events.single().cause)
    }

    private class Sample
}

@OptIn(DelicateAkkiApi::class)
private inline fun <T> withBackend(backend: LogBackend, block: () -> T): T {
    val installation = Log.install(backend)
    return try {
        block()
    } finally {
        installation.uninstall()
    }
}

private data class Event(
    val name: String,
    val level: Level,
    val message: String,
    val fields: Map<String, Any?>,
    val cause: Throwable? = null,
)

private class RecordingBackend(
    private val enabledLevels: Set<Level> = Level.entries.toSet(),
) : LogBackend {
    val events: MutableList<Event> = mutableListOf()
    val resolutions: MutableList<Pair<String, Level>> = mutableListOf()

    override fun resolve(name: String, level: Level): Sink? {
        resolutions += name to level
        if (level !in enabledLevels) return null
        return Sink { message, cause, fields ->
            events += Event(name, level, message, fields, cause)
        }
    }
}

private class RecordingLogger : Logger {
    override val name: String = "custom"
    val events: MutableList<Event> = mutableListOf()

    override fun isEnabled(level: Level): Boolean = true

    override fun emit(
        level: Level,
        message: String,
        cause: Throwable?,
        fields: Map<String, Any?>,
    ): Unit {
        events += Event(name, level, message, fields, cause)
    }
}
