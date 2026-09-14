package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.internal.DefaultBackend
import io.akki.internal.chooseBackend
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIsNot
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmBackendDiscoveryTest {
    @Test
    fun `falls back to the default backend when nothing is declared`(): Unit {
        assertTrue(chooseBackend(emptyList()) is DefaultBackend)
    }

    @Test
    fun `uses the single declared backend`(): Unit {
        val declared = RecordingOnlyBackend()

        val chosen = chooseBackend(listOf(declared))

        assertSame(declared, chosen)
        assertIsNot<DefaultBackend>(chosen)
    }

    @Test
    fun `names every candidate when several are declared`(): Unit {
        val first = RecordingOnlyBackend()
        val second = SilentBackend()

        val output = captureError { assertSame(first, chooseBackend(listOf(first, second))) }

        assertContains(output, RecordingOnlyBackend::class.java.name)
        assertContains(output, SilentBackend::class.java.name)
    }

    @Test
    fun `picks the same backend whatever order the service loader reports`(): Unit {
        val recording = RecordingOnlyBackend()
        val silent = SilentBackend()

        val chosen = silencingError { chooseBackend(listOf(recording, silent)) }
        val reversed = silencingError { chooseBackend(listOf(silent, recording)) }

        assertSame(recording, chosen)
        assertSame(recording, reversed)
    }

    private fun <T> silencingError(block: () -> T): T {
        val original = System.err
        System.setErr(PrintStream(ByteArrayOutputStream(), true))
        try {
            return block()
        } finally {
            System.setErr(original)
        }
    }

    private fun captureError(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(buffer, true))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return buffer.toString()
    }

    private class RecordingOnlyBackend : LogBackend {
        override fun bind(name: String): LoggerBinding = LoggerBinding { Sink { _, _, _ -> } }
    }

    private class SilentBackend : LogBackend {
        override fun bind(name: String): LoggerBinding = LoggerBinding { null }
    }
}
