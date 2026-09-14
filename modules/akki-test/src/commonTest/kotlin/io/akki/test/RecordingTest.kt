package io.akki.test

import io.akki.Level
import io.akki.Log
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
    fun `disabled levels are resolved but not recorded`() {
        val backend = RecordingBackend(Level.ERROR)

        withBackend(backend) {
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

        withBackend(backend) {
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
        withBackend(backend) { Log.named("read-only").info("kept") }

        assertFalse(backend.records is MutableList<*>)
        assertFalse(RecordingLogger().records is MutableList<*>)
    }

    @Test
    fun `withBackend restores the previous backend`() {
        val outer = RecordingBackend()
        val inner = RecordingBackend()

        withBackend(outer) {
            withBackend(inner) { Log.named("nested").info("inside") }
            Log.named("nested").info("outside")
        }

        assertEquals(listOf("inside"), inner.records.map { it.message })
        assertEquals(listOf("outside"), outer.records.map { it.message })
    }

    @Test
    fun `withBackend returns the block result`() {
        assertEquals(42, withBackend(RecordingBackend()) { 42 })
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
