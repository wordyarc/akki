package dev.ashenarx.akki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

        logger.info("implemented", fields)

        assertEquals(
            listOf(Event("custom", Level.INFO, "implemented", fields)),
            logger.events,
        )
    }

    @Test
    fun levelMethodsEmitThroughTheInstalledBackend(): Unit {
        val backend = RecordingBackend()
        val logger = Log.named("checkout")
        val fields = mapOf("orderId" to 42)
        Log.install(backend)

        logger.trace("trace", fields)
        logger.debug("debug", fields)
        logger.info("info", fields)
        logger.warn("warn", fields)
        logger.error("error", fields)

        assertEquals(Level.entries, backend.events.map(Event::level))
        assertTrue(backend.events.all { it.name == "checkout" })
        assertEquals(listOf("trace", "debug", "info", "warn", "error"), backend.events.map(Event::message))
        assertTrue(backend.events.all { it.fields == fields })
    }

    @Test
    fun backendControlsLevelFiltering(): Unit {
        val backend = RecordingBackend(setOf(Level.ERROR))
        val logger = Log.named("filtered")
        Log.install(backend)

        assertFalse(logger.isEnabled(Level.INFO))
        assertTrue(logger.isEnabled(Level.ERROR))

        logger.info("ignored")
        logger.error("recorded")

        assertEquals(listOf("recorded"), backend.events.map(Event::message))
    }

    @Test
    fun existingLogUsesReplacementBackend(): Unit {
        val logger = Log.named("replaceable")
        val first = RecordingBackend()
        val second = RecordingBackend()

        Log.install(first)
        logger.info("first")
        Log.install(second)
        logger.info("second")

        assertEquals(listOf("first"), first.events.map(Event::message))
        assertEquals(listOf("second"), second.events.map(Event::message))
    }

    private class Sample
}

private data class Event(
    val name: String,
    val level: Level,
    val message: String,
    val fields: Map<String, Any?>,
)

private class RecordingBackend(
    private val enabledLevels: Set<Level> = Level.entries.toSet(),
) : LogBackend {
    val events: MutableList<Event> = mutableListOf()

    override fun resolve(name: String, level: Level): Sink? {
        if (level !in enabledLevels) return null
        return Sink { emittedLevel, message, fields ->
            events += Event(name, emittedLevel, message, fields)
        }
    }
}

private class RecordingLogger : Logger {
    override val name: String = "custom"
    val events: MutableList<Event> = mutableListOf()

    override fun isEnabled(level: Level): Boolean = true

    override fun emit(level: Level, message: String, fields: Map<String, Any?>): Unit {
        events += Event(name, level, message, fields)
    }
}
