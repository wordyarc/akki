package dev.ashenarx.akki.test

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.Log
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val backend = RecordingBackend(setOf(Level.ERROR))

        withBackend(backend) {
            Log.named("quiet").info("ignored")
            Log.named("quiet").error("kept")
        }

        assertEquals(listOf("kept"), backend.records.messages)
        assertEquals(
            listOf(Resolution("quiet", Level.INFO), Resolution("quiet", Level.ERROR)),
            backend.resolutions,
        )
    }

    @Test
    fun `withBackend restores the previous backend`() {
        val outer = RecordingBackend()
        val inner = RecordingBackend()

        withBackend(outer) {
            withBackend(inner) { Log.named("nested").info("inside") }
            Log.named("nested").info("outside")
        }

        assertEquals(listOf("inside"), inner.records.messages)
        assertEquals(listOf("outside"), outer.records.messages)
    }

    @Test
    fun `withBackend returns the block result`() {
        assertEquals(42, withBackend(RecordingBackend()) { 42 })
    }

    @Test
    fun `recording logger needs no installation`() {
        val logger = RecordingLogger("checkout", enabled = setOf(Level.ERROR))

        logger.info("ignored")
        logger.error("kept")

        assertEquals(listOf("kept"), logger.records.messages)
        assertEquals(listOf(Level.ERROR), logger.records.levels)
        assertTrue(logger.isEnabled(Level.ERROR))
    }

    @Test
    fun `recording logger reports its name`() {
        val logger = RecordingLogger()

        assertEquals("recording", logger.name)
        assertEquals("RecordingLogger(recording)", logger.toString())
    }
}
