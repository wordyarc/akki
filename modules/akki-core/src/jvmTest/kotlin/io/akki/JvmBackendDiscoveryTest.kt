package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.internal.DefaultBackend
import io.akki.internal.chooseBackend
import io.akki.internal.discover
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

        val output = captureStderr { assertSame(first, chooseBackend(listOf(first, second))) }

        assertContains(output, RecordingOnlyBackend::class.java.name)
        assertContains(output, SilentBackend::class.java.name)
    }

    @Test
    fun `picks the same backend whatever order the service loader reports`(): Unit {
        val recording = RecordingOnlyBackend()
        val silent = SilentBackend()

        lateinit var chosen: LogBackend
        lateinit var reversed: LogBackend
        captureStderr {
            chosen = chooseBackend(listOf(recording, silent))
            reversed = chooseBackend(listOf(silent, recording))
        }

        assertSame(recording, chosen)
        assertSame(recording, reversed)
    }

    @Test
    fun `skips broken service declarations and keeps the rest`(@TempDir directory: Path): Unit {
        directory.declareBackends(
            "io.akki.MissingBackend", ThrowingBackend::class.java.name, DeclaredBackend::class.java.name,
        )
        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { assertIs<DeclaredBackend>(discover(loader)) }
        }

        assertContains(output, "io.akki.MissingBackend")
        assertContains(output, ThrowingBackend::class.java.name)
        assertEquals(2, output.lines().count { it.contains("ignoring a broken backend service declaration") }, output)
    }

    @Test
    fun `falls back when a provider cannot be linked`(@TempDir directory: Path): Unit {
        directory.declareBackends(UNLINKED_BACKEND)

        val output = UnlinkedProviderLoader(directory).use { loader ->
            captureStderr { assertIs<DefaultBackend>(discover(loader)) }
        }

        assertContains(output, "akki: failed to discover backends")
        assertContains(output, "NoClassDefFoundError: provider/AbsentBase")
    }

    @Test
    fun `keeps an already loaded backend when discovery fails`(@TempDir directory: Path): Unit {
        directory.declareBackends(DeclaredBackend::class.java.name, UNLINKED_BACKEND)

        val output = UnlinkedProviderLoader(directory).use { loader ->
            captureStderr { assertIs<DeclaredBackend>(discover(loader)) }
        }

        assertContains(output, "NoClassDefFoundError: provider/AbsentBase")
    }

    private fun Path.declareBackends(vararg names: String) {
        val declarations = resolve("META-INF/services/${LogBackend::class.java.name}")
        declarations.parent.createDirectories()
        declarations.writeText(names.joinToString("\n"))
    }

    private class UnlinkedProviderLoader(directory: Path) : URLClassLoader(
        arrayOf(directory.toUri().toURL()),
        LogBackend::class.java.classLoader,
    ) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name == UNLINKED_BACKEND) throw NoClassDefFoundError("provider/AbsentBase")
            return super.loadClass(name, resolve)
        }
    }

    private companion object {
        const val UNLINKED_BACKEND: String = "io.akki.UnlinkedBackend"
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
