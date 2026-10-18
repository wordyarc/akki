@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.internal.DefaultBackend
import io.akki.internal.LogBackendFactory
import io.akki.internal.chooseBackend
import io.akki.internal.discover
import io.akki.testing.captureStderr
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
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
import org.junit.jupiter.api.assertTimeoutPreemptively
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
        assertContains(output, "broken constructor")
        assertEquals(2, output.lines().count { it.contains("skipping an invalid backend service declaration") }, output)
    }

    @Test
    fun `falls back when a provider cannot be linked`(@TempDir directory: Path): Unit {
        directory.declareBackends(UNLINKED_BACKEND)

        val output = UnlinkedProviderLoader(directory).use { loader ->
            captureStderr { assertIs<DefaultBackend>(discover(loader)) }
        }

        assertContains(output, "akki: skipping an invalid backend service declaration")
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
        assertEquals(1, output.lines().count { it.contains("skipping an invalid backend service declaration") }, output)
    }

    @Test
    fun `uses a factory if no backend is declared`(@TempDir directory: Path): Unit {
        directory.declareFactories(CreatingFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { assertIs<CreatedBackend>(discover(loader)) }
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
            "akki: no backend is available, writing INFO and higher to stderr. Add the declining provider.\n" +
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
            "akki: backend factory ${FailingFactory::class.java.name} failed and is skipped: " +
                "java.lang.IllegalStateException: broken factory",
        )
        assertContains(output, "writing INFO and higher to stderr. Add the failing provider.\nINFO  acme.Failed - failed")
    }

    @Test
    fun `a factory that fails to give its hint does not fail the lookup`(@TempDir directory: Path): Unit {
        directory.declareFactories(HintlessFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { discover(loader).record("acme.Hintless", "hintless") }
        }

        assertContains(
            output,
            "akki: backend factory ${HintlessFactory::class.java.name} failed and is skipped: " +
                "java.lang.IllegalStateException: broken hint",
        )
        assertContains(output, "Add a backend such as akki-slf4j to the runtime classpath.\nINFO  acme.Hintless - hintless")
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
    fun `propagates fatal factory constructor errors without reporting them`(@TempDir directory: Path): Unit {
        directory.declareFactories(FatalConstructorFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr {
                val failure = assertFailsWith<StackOverflowError> { discover(loader) }
                assertEquals("fatal constructor", failure.message)
            }
        }

        assertEquals("", output)
    }

    @Test
    fun `the next lookup retries discovery after a fatal backend constructor`(@TempDir directory: Path): Unit {
        directory.declareBackends(FatalConstructorBackend::class.java.name)

        val output = IsolatedLoader(directory).use { loader ->
            val lookup = loader.loadClass(IsolatedLookup::class.java.name)
                .getDeclaredConstructor()
                .newInstance() as BooleanSupplier

            captureStderr {
                val failure = assertFailsWith<StackOverflowError> { lookup.asBoolean }
                assertEquals("fatal constructor", failure.message)
                directory.declareBackends(DeclaredBackend::class.java.name)
                assertFalse(lookup.asBoolean)
            }
        }

        assertEquals("", output)
    }

    @Test
    fun `falls back when the service resources cannot be enumerated`(): Unit {
        val loader = object : ClassLoader(LogBackend::class.java.classLoader) {
            override fun getResources(name: String): Enumeration<URL> = error("broken resources")
        }

        val output = captureStderr { assertIs<DefaultBackend>(discover(loader)) }

        assertContains(output, "akki: backend discovery failed")
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
        assertContains(output, "akki: an I/O error stopped the backend service lookup")
        assertFalse(output.contains("skipping an invalid backend service declaration"), output)
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

        assertContains(output, "akki: an I/O error stopped the backend service lookup")
        assertFalse(output.contains("skipping an invalid backend service declaration"), output)
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

    @Test
    fun `selects the declared backend named by the property`(@TempDir directory: Path): Unit {
        directory.declareBackends(DeclaredBackend::class.java.name, SecondDeclaredBackend::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { assertIs<SecondDeclaredBackend>(discover(loader, SecondDeclaredBackend::class.java.name)) }
        }

        assertEquals("", output)
    }

    @Test
    fun `selects a factory backend over a declared one`(@TempDir directory: Path): Unit {
        directory.declareBackends(DeclaredBackend::class.java.name)
        directory.declareFactories(CreatingFactory::class.java.name)

        val output = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            captureStderr { assertIs<CreatedBackend>(discover(loader, CreatedBackend::class.java.name)) }
        }

        assertEquals("", output)
    }

    @Test
    fun `rejects a selected backend that is not on the classpath`(@TempDir directory: Path): Unit {
        directory.declareBackends(DeclaredBackend::class.java.name)
        directory.declareFactories(CreatingFactory::class.java.name)

        val failure = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            assertFailsWith<IllegalStateException> { discover(loader, "acme.Missing") }
        }

        assertEquals(
            "akki: unknown backend 'acme.Missing' for 'io.akki.backend': the classpath declares " +
                "${DeclaredBackend::class.java.name}, ${CreatedBackend::class.java.name}",
            failure.message,
        )
    }

    @Test
    fun `rejects a selected backend when nothing is declared`(): Unit {
        val failure = assertFailsWith<IllegalStateException> { discover(javaClass.classLoader, "acme.Missing") }

        assertEquals(
            "akki: unknown backend 'acme.Missing' for 'io.akki.backend': the classpath declares no backend",
            failure.message,
        )
    }

    @Test
    fun `reports the factory hint when the selected factory declines`(@TempDir directory: Path): Unit {
        directory.declareFactories(DecliningFactory::class.java.name)

        val failure = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            assertFailsWith<IllegalStateException> { discover(loader, "acme.Declined") }
        }

        assertEquals(
            "akki: the backend 'acme.Declined' selected by 'io.akki.backend' is not available. Add the declining provider.",
            failure.message,
        )
    }

    @Test
    fun `reports the cause when the selected factory fails`(@TempDir directory: Path): Unit {
        directory.declareFactories(FailingFactory::class.java.name)

        val failure = URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            assertFailsWith<IllegalStateException> { discover(loader, "acme.Failing") }
        }

        assertEquals("akki: the backend 'acme.Failing' selected by 'io.akki.backend' failed to start", failure.message)
        assertEquals("broken factory", failure.cause?.message)
    }

    @Test
    fun `propagates a fatal error from the selected factory`(@TempDir directory: Path): Unit {
        directory.declareFactories(FatalFactory::class.java.name)

        URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
            assertFailsWith<StackOverflowError> { discover(loader, "acme.Fatal") }
        }
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

    private class IsolatedLoader(directory: Path? = null, private val lookup: (String) -> Unit = {}) : URLClassLoader(
        (listOfNotNull(directory?.toFile()) + System.getProperty("java.class.path").split(File.pathSeparator).map(::File))
            .map { it.toURI().toURL() }
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
        override fun bind(loggerName: String): LoggerBinding = LoggerBinding { Sink { _, _, _ -> } }
    }

    private class SilentBackend : LogBackend {
        override fun bind(loggerName: String): LoggerBinding = LoggerBinding { null }
    }
}

class DeclaredBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding = LoggerBinding { null }
}

class SecondDeclaredBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding = LoggerBinding { null }
}

class CreatedBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding = LoggerBinding { null }
}

class ThrowingBackend : LogBackend {
    init {
        error("broken constructor")
    }

    override fun bind(loggerName: String): LoggerBinding = LoggerBinding { null }
}

class FatalConstructorBackend : LogBackend {
    init {
        throw StackOverflowError("fatal constructor")
    }

    override fun bind(loggerName: String): LoggerBinding = LoggerBinding { null }
}

class FatalConstructorFactory : LogBackendFactory {
    init {
        throw StackOverflowError("fatal constructor")
    }

    override val backendClassName: String = "acme.FatalConstructor"

    override fun createBackend(): LogBackend? = null

    override fun hintOnMissing(): String = "Add the fatal constructor provider."
}

class IsolatedLookup : BooleanSupplier {
    override fun getAsBoolean(): Boolean = Log.named("discovery.isolated").isEnabled(Level.INFO)
}

class CreatingFactory : LogBackendFactory {
    override val backendClassName: String = CreatedBackend::class.java.name

    override fun createBackend(): LogBackend = CreatedBackend()

    override fun hintOnMissing(): String = "Add the creating provider."
}

class DecliningFactory : LogBackendFactory {
    override val backendClassName: String = "acme.Declined"

    override fun createBackend(): LogBackend? = null

    override fun hintOnMissing(): String = "Add the declining provider."
}

class FailingFactory : LogBackendFactory {
    override val backendClassName: String = "acme.Failing"

    override fun createBackend(): LogBackend? = error("broken factory")

    override fun hintOnMissing(): String = "Add the failing provider."
}

class HintlessFactory : LogBackendFactory {
    override val backendClassName: String = "acme.Hintless"

    override fun createBackend(): LogBackend? = null

    override fun hintOnMissing(): String = error("broken hint")
}

class FatalFactory : LogBackendFactory {
    override val backendClassName: String = "acme.Fatal"

    override fun createBackend(): LogBackend? = throw StackOverflowError("fatal")

    override fun hintOnMissing(): String = "Add the fatal provider."
}
