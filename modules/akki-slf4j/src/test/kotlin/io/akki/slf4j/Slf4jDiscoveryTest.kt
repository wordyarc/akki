package io.akki.slf4j

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LogbackServiceProvider
import ch.qos.logback.core.read.ListAppender
import io.akki.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOP_FallbackServiceProvider

class Slf4jDiscoveryTest {
    @Test
    fun `routes logs through the slf4j provider on the classpath`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.reset()
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).apply {
            level = LogbackLevel.TRACE
            addAppender(appender)
        }

        Log.named("discovered").info("without an install")

        val event = appender.list.single()
        assertEquals("discovered", event.loggerName)
        assertEquals("without an install", event.message)
    }

    @Test
    fun `a provider on the classpath enables the backend`() {
        assertEquals(Slf4jBackend::class.java.name, createBackend(classpath())?.javaClass?.name)
    }

    @Test
    fun `a missing provider disables the backend without slf4j warnings`() {
        val output = captureStderr { assertNull(createBackend(classpath().without("logback-"))) }

        assertEquals("", output)
    }

    @Test
    fun `a missing slf4j API disables the backend`() {
        assertNull(createBackend(classpath().without("logback-", "slf4j-api-")))
    }

    @Test
    fun `declines the backend if slf4j selects a nop provider`(@TempDir directory: Path) {
        directory.declareProviders(NOP_FallbackServiceProvider::class.java.name)

        assertNull(createBackend(listOf(directory.toFile()) + classpath()))
    }

    @Test
    fun `a nop provider set through the slf4j property disables the backend`() {
        val backend = withProviderProperty(NOP_FallbackServiceProvider::class.java.name) { createBackend(classpath()) }

        assertNull(backend)
    }

    @Test
    fun `the slf4j provider property enables the backend without a service declaration`() {
        val backend = withProviderProperty(LogbackServiceProvider::class.java.name) {
            UndeclaredProvidersLoader(classpath().urls()).use { it.createBackend() }
        }

        assertEquals(Slf4jBackend::class.java.name, backend?.javaClass?.name)
    }

    @Test
    fun `a broken provider declaration does not prevent backend creation`(@TempDir directory: Path) {
        directory.declareProviders("io.akki.slf4j.MissingProvider")

        val backend = createBackend(listOf(directory.toFile()) + classpath())

        assertEquals(Slf4jBackend::class.java.name, backend?.javaClass?.name)
    }

    @Test
    fun `a provider hidden from the slf4j loader disables the backend without warnings`() {
        val (api, rest) = classpath().partition { it.name.startsWith("slf4j-api-") }

        val output = URLClassLoader(api.urls(), ClassLoader.getPlatformClassLoader()).use { parent ->
            captureStderr { assertNull(createBackend(rest, parent)) }
        }

        assertEquals("", output)
    }

    @Test
    fun `declines the backend during concurrent slf4j initialization without a provider`() {
        val initializing = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val loader = object : URLClassLoader(classpath().without("logback-").urls(), ClassLoader.getPlatformClassLoader()) {
            override fun getResources(name: String): Enumeration<URL> {
                if (name == PROVIDER_DECLARATIONS && Thread.currentThread().name == INITIALIZER) {
                    initializing.countDown()
                    proceed.await()
                }
                return super.getResources(name)
            }
        }
        val initializer = thread(name = INITIALIZER) {
            loader.loadClass(LoggerFactory::class.java.name).getMethod("getILoggerFactory").invoke(null)
        }

        loader.use {
            try {
                assertTrue(initializing.await(10, TimeUnit.SECONDS))
                assertNull(it.createBackend())
            } finally {
                proceed.countDown()
                initializer.join()
            }
        }
    }

    private fun classpath(): List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)

    private fun List<File>.without(vararg libraries: String): List<File> =
        filterNot { file -> libraries.any { file.name.startsWith(it) } }

    private fun List<File>.urls(): Array<URL> = map { it.toURI().toURL() }.toTypedArray()

    private fun createBackend(classpath: List<File>, parent: ClassLoader = ClassLoader.getPlatformClassLoader()): Any? =
        URLClassLoader(classpath.urls(), parent).use { it.createBackend() }

    private fun ClassLoader.createBackend(): Any? {
        val factory = loadClass(Slf4jBackendFactory::class.java.name).getDeclaredConstructor().newInstance()
        return factory.javaClass.getMethod("createBackend").invoke(factory)
    }

    private fun Path.declareProviders(vararg names: String) {
        val declarations = resolve(PROVIDER_DECLARATIONS)
        declarations.parent.createDirectories()
        declarations.writeText(names.joinToString("\n"))
    }

    private fun <T> withProviderProperty(provider: String, block: () -> T): T {
        System.setProperty(PROVIDER_PROPERTY, provider)
        try {
            return block()
        } finally {
            System.clearProperty(PROVIDER_PROPERTY)
        }
    }

    private fun captureStderr(block: () -> Unit): String {
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
}

private class UndeclaredProvidersLoader(urls: Array<URL>) : URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
    override fun getResources(name: String): Enumeration<URL> =
        if (name == PROVIDER_DECLARATIONS) Collections.emptyEnumeration() else super.getResources(name)
}

private const val PROVIDER_DECLARATIONS: String = "META-INF/services/org.slf4j.spi.SLF4JServiceProvider"
private const val PROVIDER_PROPERTY: String = "slf4j.provider"
private const val INITIALIZER: String = "slf4j-initializer"
