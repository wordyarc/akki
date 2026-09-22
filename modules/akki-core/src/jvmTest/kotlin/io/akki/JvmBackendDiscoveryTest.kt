package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.internal.DefaultBackend
import io.akki.internal.chooseBackend
import io.akki.internal.discover
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

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

    @Test
    fun `skips broken service declarations and keeps the rest`(@TempDir directory: Path): Unit {
        val declarations = directory.resolve("META-INF/services/${LogBackend::class.java.name}")
        declarations.parent.createDirectories()
        declarations.writeText(
            listOf("io.akki.MissingBackend", ThrowingBackend::class.java.name, DeclaredBackend::class.java.name)
                .joinToString("\n"),
        )
        val loader = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader)

        val output = captureError { assertIs<DeclaredBackend>(discover(loader)) }

        assertContains(output, "io.akki.MissingBackend")
        assertContains(output, ThrowingBackend::class.java.name)
        assertEquals(2, output.lines().count { it.contains("ignoring a broken backend service declaration") }, output)
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

class DeclaredBackend : LogBackend {
    override fun bind(name: String): LoggerBinding = LoggerBinding { null }
}

class ThrowingBackend : LogBackend {
    init {
        error("broken constructor")
    }

    override fun bind(name: String): LoggerBinding = LoggerBinding { null }
}
