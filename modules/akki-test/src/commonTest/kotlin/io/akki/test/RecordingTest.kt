package io.akki.test

import io.akki.Level
import io.akki.Log
import io.akki.LogScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingTest {
    @Test
    fun `records everything the backend emits`() {
        val cause = IllegalStateException("boom")
        val records = recordLogs {
            Log.named("orders").info("placed", fields = mapOf("id" to 1))
            Log.named("orders").error("failed", cause)
        }

        assertEquals(
            listOf(
                LogRecord("orders", Level.INFO, "placed", fields = mapOf("id" to 1)),
                LogRecord("orders", Level.ERROR, "failed", cause),
            ),
            records,
        )
    }

    @Test
    fun `nested captures record separately`() {
        val outer = recordLogs {
            val inner = recordLogs { Log.named("nested").info("inside") }
            Log.named("nested").info("outside")

            assertEquals(listOf("inside"), inner.map { it.message })
        }

        assertEquals(listOf("outside"), outer.map { it.message })
    }

    @Test
    fun `records below the requested level are not captured`() {
        val records = recordLogs(Level.WARN) {
            Log.named("quiet").info("ignored")
            Log.named("quiet").error("kept")
        }

        assertEquals(listOf("kept"), records.map { it.message })
    }

    @Test
    fun `disabled levels are resolved but not recorded`() {
        val backend = RecordingBackend(Level.ERROR)

        LogScope(backend).run {
            Log.named("quiet").info("ignored")
            Log.named("quiet").error("kept")
        }

        assertEquals(listOf("kept"), backend.records.map { it.message })
        assertEquals(
            listOf(Resolution("quiet", Level.INFO), Resolution("quiet", Level.ERROR)),
            backend.resolutions,
        )
    }

    @Test
    fun `records hands out a snapshot that later records do not change`() {
        val backend = RecordingBackend()

        LogScope(backend).run {
            Log.named("snapshot").info("first")
            val taken = backend.records
            Log.named("snapshot").info("second")

            assertEquals(listOf("first"), taken.map { it.message })
            assertEquals(listOf("first", "second"), backend.records.map { it.message })
        }
    }

    @Test
    fun `records cannot be written through the list it returns`() {
        val backend = RecordingBackend()
        LogScope(backend).run { Log.named("read-only").info("kept") }

        assertFalse(backend.records is MutableList<*>)
        assertFalse(RecordingLogger().records is MutableList<*>)
    }

    @Test
    fun `recording logger needs no installation`() {
        val logger = RecordingLogger("checkout", minLevel = Level.ERROR)

        logger.info("ignored")
        logger.error("kept")

        assertEquals(listOf("kept"), logger.records.map { it.message })
        assertEquals(listOf(Level.ERROR), logger.records.map { it.level })
        assertTrue(logger.isEnabled(Level.ERROR))
    }

    @Test
    fun `recording logger reports its name`() {
        val logger = RecordingLogger()

        assertEquals("recording", logger.name)
        assertEquals("RecordingLogger(recording)", logger.toString())
    }
}
