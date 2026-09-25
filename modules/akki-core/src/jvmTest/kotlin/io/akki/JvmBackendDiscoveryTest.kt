@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.internal.DefaultBackend
import io.akki.internal.LogBackendFactory
import io.akki.internal.chooseBackend
import io.akki.internal.discover
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.util.Collections
import java.util.Enumeration
import java.util.function.BooleanSupplier
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertTimeoutPreemptively

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

        assertContains(output, "akki: ignoring a broken backend service declaration")
        assertContains(output, "NoClassDefFoundError: provider/AbsentBase")
    }

    @Test
    fun `keeps an already loaded backend when a provider cannot be linked`(@TempDir directory: Path): Unit {
        directory.declareBackends(DeclaredBackend::class.java.name, UNLINKED_BACKEND)

        val output = UnlinkedProviderLoader(directory).use { loader ->
            captureStderr { assertIs<DeclaredBackend>(discover(loader)) }
        }

        assertContains(output, "NoClassDefFoundError: provider/AbsentBase")
    }

    @Test
    fun `continues to a valid backend after a provider cannot be linked`(@TempDir directory: Path): Unit {
        directory.declareBackends(UNLINKED_BACKEND, DeclaredBackend::class.java.name)

        val output = UnlinkedProviderLoader(directory).use { loader ->
            captureStderr { assertIs<DeclaredBackend>(discover(loader)) }
        }

        assertContains(output, "NoClassDefFoundError: provider/AbsentBase")
        assertEquals(1, output.lines().count { it.contains("ignoring a broken backend service declaration") }, output)
    }

    @Test
    fun `uses a factory if no backend is declared`(@TempDir directory: Path): Unit {
        directory.declareFactories(CreatingFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { assertIs<DeclaredBackend>(discover(loader)) }
        }

        assertEquals("", output)
    }

    @Test
    fun `skips factories if a backend is declared`(@TempDir directory: Path): Unit {
        directory.declareBackends(DeclaredBackend::class.java.name)
        directory.declareFactories(FailingFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { assertIs<DeclaredBackend>(discover(loader)) }
        }

        assertEquals("", output)
    }

    @Test
    fun `includes the factory hint when backend creation returns null`(@TempDir directory: Path): Unit {
        directory.declareFactories(DecliningFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { discover(loader).record("acme.Declined", "declined") }
        }

        assertContains(
            output,
            "akki: no backend found on the classpath, writing to stderr at INFO. Add the declining provider.\n" +
                "INFO  acme.Declined - declined",
        )
    }

    @Test
    fun `prints the factory failure and its fallback hint`(@TempDir directory: Path): Unit {
        directory.declareFactories(FailingFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { discover(loader).record("acme.Failed", "failed") }
        }

        assertContains(
            output,
            "akki: skipping backend factory ${FailingFactory::class.java.name} after a failure: " +
                "java.lang.IllegalStateException: broken factory",
        )
        assertContains(output, "writing to stderr at INFO. Add the failing provider.\nINFO  acme.Failed - failed")
    }

    @Test
    fun `propagates fatal factory errors without reporting them`(@TempDir directory: Path): Unit {
        directory.declareFactories(FatalFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { assertFailsWith<StackOverflowError> { discover(loader) } }
        }

        assertEquals("", output)
    }

    @Test
    fun `falls back when the service resources cannot be enumerated`(): Unit {
        val loader = object : ClassLoader(LogBackend::class.java.classLoader) {
            override fun getResources(name: String): Enumeration<URL> = error("broken resources")
        }

        val output = captureStderr { assertIs<DefaultBackend>(discover(loader)) }

        assertContains(output, "akki: failed to discover backends")
        assertContains(output, "IllegalStateException: broken resources")
    }

    @Test
    fun `an I O failure interrupts service enumeration without retrying`(): Unit {
        var attempts = 0
        val loader = object : ClassLoader(LogBackend::class.java.classLoader) {
            override fun getResources(name: String): Enumeration<URL> {
                check(!Thread.currentThread().isInterrupted) { "discovery was interrupted" }
                attempts++
                throw IOException("broken resources")
            }
        }

        val output = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            captureStderr { assertIs<DefaultBackend>(discover(loader)) }
        }

        assertEquals(1, attempts)
        assertContains(output, "akki: backend service enumeration interrupted by an I/O failure")
        assertFalse(output.contains("ignoring a broken backend service declaration"), output)
    }

    @Test
    fun `an I O failure keeps the providers already loaded`(@TempDir directory: Path): Unit {
        val valid = directory.declareBackends(DeclaredBackend::class.java.name)
        val missing = directory.resolve("missing-services").toUri().toURL()
        val loader = object : ClassLoader(LogBackend::class.java.classLoader) {
            override fun getResources(name: String): Enumeration<URL> =
                Collections.enumeration(listOf(valid, missing))
        }

        val output = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            captureStderr { assertIs<DeclaredBackend>(discover(loader)) }
        }

        assertContains(output, "akki: backend service enumeration interrupted by an I/O failure")
        assertFalse(output.contains("ignoring a broken backend service declaration"), output)
    }

    @Test
    fun `a fatal error escapes discovery unreported`(): Unit {
        val loader = object : ClassLoader(LogBackend::class.java.classLoader) {
            override fun getResources(name: String): Enumeration<URL> = throw StackOverflowError("fatal")
        }

        val output = captureStderr { assertFailsWith<StackOverflowError> { discover(loader) } }

        assertEquals("", output)
    }

    @Test
    fun `the next lookup retries a discovery that failed fatally`(): Unit {
        var lookups = 0
        IsolatedLoader { name ->
            if (name == "META-INF/services/${LogBackend::class.java.name}") {
                lookups++
                if (lookups == 1) throw StackOverflowError("fatal")
            }
        }.use { loader ->
            val lookup = loader.loadClass(IsolatedLookup::class.java.name)
                .getDeclaredConstructor()
                .newInstance() as BooleanSupplier

            assertFailsWith<StackOverflowError> { lookup.asBoolean }
            assertTrue(lookup.asBoolean)
        }

        assertEquals(2, lookups)
    }

    private fun Path.declareBackends(vararg names: String): URL = declare(LogBackend::class.java, names)

    private fun Path.declareFactories(vararg names: String): URL = declare(LogBackendFactory::class.java, names)

    private fun Path.declare(service: Class<*>, names: Array<out String>): URL {
        val declarations = resolve("META-INF/services/${service.name}")
        declarations.parent.createDirectories()
        declarations.writeText(names.joinToString("\n"))
        return declarations.toUri().toURL()
    }

    private fun LogBackend.record(name: String, message: String) {
        bind(name).resolve(Level.INFO)?.emit(message, null, emptyMap())
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

    private class IsolatedLoader(private val lookup: (String) -> Unit) : URLClassLoader(
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map { File(it).toURI().toURL() }
            .toTypedArray(),
        ClassLoader.getPlatformClassLoader(),
    ) {
        override fun getResources(name: String): Enumeration<URL> {
            lookup(name)
            return super.getResources(name)
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

class IsolatedLookup : BooleanSupplier {
    override fun getAsBoolean(): Boolean = Log.named("discovery.isolated").isEnabled(Level.INFO)
}

class CreatingFactory : LogBackendFactory {
    override fun createBackend(): LogBackend = DeclaredBackend()

    override fun hintOnMissing(): String = "Add the creating provider."
}

class DecliningFactory : LogBackendFactory {
    override fun createBackend(): LogBackend? = null

    override fun hintOnMissing(): String = "Add the declining provider."
}

class FailingFactory : LogBackendFactory {
    override fun createBackend(): LogBackend? = error("broken factory")

    override fun hintOnMissing(): String = "Add the failing provider."
}

class FatalFactory : LogBackendFactory {
    override fun createBackend(): LogBackend? = throw StackOverflowError("fatal")

    override fun hintOnMissing(): String = "Add the fatal provider."
}
