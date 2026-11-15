@file:OptIn(InternalAkkiApi::class)

package io.akki.system

import io.akki.InternalAkkiApi
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.internal.LogBackendFactory
import io.akki.testing.captureStderr
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.logging.Level as JulLevel
import java.util.logging.Logger as JulLogger
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class SystemLoggerDiscoveryTest {
    private val handler = RecordingHandler()
    private val discovered: JulLogger = JulLogger.getLogger(LOGGER)

    @BeforeTest
    fun record() {
        discovered.level = JulLevel.ALL
        discovered.useParentHandlers = false
        discovered.addHandler(handler)
    }

    @AfterTest
    fun release() {
        discovered.removeHandler(handler)
    }

    @Test
    fun `registers a factory that names and creates the backend`() {
        val factory = ServiceLoader.load(LogBackendFactory::class.java).single()

        assertEquals(SystemLoggerBackend::class.java.name, factory.backendClassName)
        assertTrue(factory.createBackend() is SystemLoggerBackend)
    }

    @Test
    fun `logs through System Logger without an install`() {
        val output = captureStderr { isolatedRuntime().use { it.writeLog() } }

        val message: String? = handler.records.single().message
        assertEquals("from isolated runtime", message)
        assertEquals("", output)
    }

    @Test
    fun `a declared backend wins over System Logger without a selection`(@TempDir directory: Path) {
        val output = captureStderr { isolatedRuntime(directory.declareStderrBackend()).use { it.writeLog() } }

        assertContains(output, "AKKI-DECLARED $LOGGER - from isolated runtime")
        assertTrue(handler.records.isEmpty())
    }

    @Test
    fun `the backend property selects System Logger over a declared backend`(@TempDir directory: Path) {
        val output = captureStderr {
            withProperty(BACKEND_PROPERTY, SystemLoggerBackend::class.java.name) {
                isolatedRuntime(directory.declareStderrBackend()).use { it.writeLog() }
            }
        }

        val message: String? = handler.records.single().message
        assertEquals("from isolated runtime", message)
        assertEquals("", output)
    }

    private fun isolatedRuntime(vararg directories: Path): URLClassLoader =
        URLClassLoader(
            (directories.map(Path::toFile) + classpath()).map { it.toURI().toURL() }.toTypedArray(),
            ClassLoader.getPlatformClassLoader(),
        )

    private fun Path.declareStderrBackend(): Path {
        resolve("META-INF/services/${LogBackend::class.java.name}").also { it.parent.createDirectories() }
            .writeText(StderrBackend::class.java.name)
        return this
    }

    private fun classpath(): List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)

    private fun ClassLoader.writeLog() {
        val logger = loadClass("io.akki.Log").getMethod("named", String::class.java).invoke(null, LOGGER)
        loadClass("io.akki.Logger").getMethod("info", String::class.java, Throwable::class.java, Map::class.java)
            .invoke(logger, "from isolated runtime", null, emptyMap<String, Any?>())
    }

    private fun <T> withProperty(name: String, value: String, block: () -> T): T {
        val previous = System.setProperty(name, value)
        try {
            return block()
        } finally {
            if (previous == null) System.clearProperty(name) else System.setProperty(name, previous)
        }
    }

    private companion object {
        const val LOGGER: String = "io.akki.system.discovered"
        const val BACKEND_PROPERTY: String = "io.akki.backend"
    }
}

class StderrBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding =
        LoggerBinding { Sink { message, _, _ -> System.err.println("AKKI-DECLARED $loggerName - $message") } }
}
