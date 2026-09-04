package dev.ashenarx.akki.test

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.Log
import dev.ashenarx.akki.error
import dev.ashenarx.akki.info
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingTest {
    @Test
    fun recordsEverythingTheBackendEmits() {
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
    fun disabledLevelsAreResolvedButNotRecorded() {
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
    fun withBackendRestoresThePreviousBackend() {
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
    fun withBackendReturnsTheBlockResult() {
        assertEquals(42, withBackend(RecordingBackend()) { 42 })
    }

    @Test
    fun recordingLoggerNeedsNoInstallation() {
        val logger = RecordingLogger("checkout", enabled = setOf(Level.ERROR))

        logger.info("ignored")
        logger.error("kept")

        assertEquals(listOf("kept"), logger.records.messages)
        assertEquals(listOf(Level.ERROR), logger.records.levels)
        assertTrue(logger.isEnabled(Level.ERROR))
    }

    @Test
    fun recordingLoggerReportsItsName() {
        val logger = RecordingLogger()

        assertEquals("recording", logger.name)
        assertEquals("RecordingLogger(recording)", logger.toString())
    }
}
