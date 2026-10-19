package io.akki.slf4j

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LogbackServiceProvider
import ch.qos.logback.core.read.ListAppender
import io.akki.Log
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.testing.BackgroundTask
import io.akki.testing.awaitSignal
import io.akki.testing.captureStderr
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.CountDownLatch
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `accepts a nop provider selected before another provider`(@TempDir directory: Path) {
        directory.declareProviders(NOP_FallbackServiceProvider::class.java.name)

        val backend = createBackend(listOf(directory.toFile()) + classpath())

        assertEquals(Slf4jBackend::class.java.name, backend?.javaClass?.name)
    }

    @Test
    fun `an explicit slf4j nop provider keeps logging silent`() {
        val output = captureStderr {
            URLClassLoader((nopProvider() + classpath().without("logback-")).urls(), ClassLoader.getPlatformClassLoader())
                .use { it.writeLog() }
        }

        assertEquals("", output)
    }

    @Test
    fun `a nop provider selected through the slf4j property keeps logging silent`() {
        val output = captureStderr {
            withProviderProperty("org.slf4j.nop.NOPServiceProvider") {
                URLClassLoader((nopProvider() + classpath()).urls(), ClassLoader.getPlatformClassLoader())
                    .use { it.writeLog() }
            }
        }

        assertContains(output, "Attempting to load provider \"org.slf4j.nop.NOPServiceProvider\"")
        assertFalse(output.contains("akki:"), output)
        assertFalse(output.contains("from isolated runtime"), output)
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
    fun `slf4j reports broken declarations when no working provider remains`(@TempDir directory: Path) {
        directory.declareProviders("io.akki.slf4j.MissingProvider")

        val output = captureStderr {
            URLClassLoader(
                (listOf(directory.toFile()) + classpath().without("logback-")).urls(),
                ClassLoader.getPlatformClassLoader(),
            ).use { it.writeLog() }
        }

        assertContains(output, "io.akki.slf4j.MissingProvider")
        assertContains(output, "No SLF4J providers were found")
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
                    proceed.awaitSignal("SLF4J initialization was not released")
                }
                return super.getResources(name)
            }
        }
        loader.use {
            BackgroundTask(INITIALIZER) {
                it.loadClass(LoggerFactory::class.java.name).getMethod("getILoggerFactory").invoke(null)
            }.use { initializer ->
                try {
                    initializing.awaitSignal("SLF4J initialization did not reach provider discovery")
                    BackgroundTask("akki-discovery") { it.createBackend() }.use { discovery ->
                        assertNull(discovery.await())
                    }
                } finally {
                    proceed.countDown()
                }
                initializer.await()
            }
        }
    }

    @Test
    fun `names the backend it creates`() {
        assertEquals(Slf4jBackend::class.java.name, Slf4jBackendFactory().backendClassName)
    }

    @Test
    fun `a declared backend wins over slf4j without a selection`(@TempDir directory: Path) {
        val output = captureStderr { isolatedRuntime(directory.declareStderrBackend()).use { it.writeLog() } }

        assertContains(output, "AKKI-DECLARED discovered - from isolated runtime")
        assertFalse(output.contains("AKKI-SLF4J"), output)
    }

    @Test
    fun `the backend property selects slf4j over a declared backend`(@TempDir directory: Path) {
        val output = captureStderr {
            withProperty(BACKEND_PROPERTY, Slf4jBackend::class.java.name) {
                isolatedRuntime(directory.declareStderrBackend()).use { it.writeLog() }
            }
        }

        assertContains(output, "AKKI-SLF4J discovered - from isolated runtime")
        assertFalse(output.contains("AKKI-DECLARED"), output)
    }

    @Test
    fun `a selected slf4j backend without a provider fails with the provider hint`() {
        val failure = withProperty(BACKEND_PROPERTY, Slf4jBackend::class.java.name) {
            URLClassLoader(classpath().without("logback-").urls(), ClassLoader.getPlatformClassLoader()).use { loader ->
                assertFailsWith<InvocationTargetException> { loader.writeLog() }
            }
        }

        assertEquals(
            "akki: the backend '${Slf4jBackend::class.java.name}' set by the system property '$BACKEND_PROPERTY' " +
                "is not available. Add an SLF4J 2 provider to the runtime classpath, for example logback-classic.",
            failure.cause?.message,
        )
    }

    @Test
    fun `rejects an unknown slf4j setting in a classpath resource`(@TempDir directory: Path) {
        directory.akkiProperties("io.akki.slf4j.contxt=mdc")

        val failure = isolatedRuntime(directory).use { assertFailsWith<InvocationTargetException> { it.writeLog() } }

        val message = failure.cause?.message.orEmpty()
        assertContains(message, "akki: unknown property 'io.akki.slf4j.contxt' in file:")
        assertContains(message, "META-INF/akki.properties: expected 'io.akki.slf4j.context'")
    }

    @Test
    fun `a classpath resource selects the mdc target`(@TempDir directory: Path) {
        directory.akkiProperties("io.akki.slf4j.context=mdc")
        directory.resolve("logback-test.xml").writeText(
            """
            <configuration>
                <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
                    <target>System.err</target>
                    <encoder>
                        <pattern>AKKI-MDC %X{requestId} %logger - %msg%kvp%n</pattern>
                    </encoder>
                </appender>
                <root level="INFO">
                    <appender-ref ref="STDERR"/>
                </root>
            </configuration>
            """.trimIndent(),
        )

        val output = captureStderr { isolatedRuntime(directory).use { it.writeLog(context = mapOf("requestId" to 7)) } }

        assertContains(output, "AKKI-MDC 7 discovered - from isolated runtime\n")
        assertFalse(output.contains("requestId=\"7\""), output)
    }

    private fun Path.akkiProperties(content: String) {
        val file = resolve("META-INF/akki.properties")
        file.parent.createDirectories()
        file.writeText(content + "\n")
    }

    private fun isolatedRuntime(directory: Path): URLClassLoader =
        URLClassLoader((listOf(directory.toFile()) + classpath()).urls(), ClassLoader.getPlatformClassLoader())

    private fun Path.declareStderrBackend(): Path {
        resolve("META-INF/services/${LogBackend::class.java.name}").also { it.parent.createDirectories() }
            .writeText(StderrBackend::class.java.name)
        resolve("logback-test.xml").writeText(
            """
            <configuration>
                <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
                    <target>System.err</target>
                    <encoder>
                        <pattern>AKKI-SLF4J %logger - %msg%n</pattern>
                    </encoder>
                </appender>
                <root level="INFO">
                    <appender-ref ref="STDERR"/>
                </root>
            </configuration>
            """.trimIndent(),
        )
        return this
    }

    private fun classpath(): List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)

    private fun nopProvider(): List<File> =
        System.getProperty("akki.slf4j.nop.classpath").split(File.pathSeparator).map(::File)

    private fun List<File>.without(vararg libraries: String): List<File> =
        filterNot { file -> libraries.any { file.name.startsWith(it) } }

    private fun List<File>.urls(): Array<URL> = map { it.toURI().toURL() }.toTypedArray()

    private fun createBackend(classpath: List<File>, parent: ClassLoader = ClassLoader.getPlatformClassLoader()): Any? =
        URLClassLoader(classpath.urls(), parent).use { it.createBackend() }

    private fun ClassLoader.createBackend(): Any? {
        val factory = loadClass(Slf4jBackendFactory::class.java.name).getDeclaredConstructor().newInstance()
        return factory.javaClass.getMethod("createBackend").invoke(factory)
    }

    private fun ClassLoader.writeLog(context: Map<String, Any?> = emptyMap()) {
        val logger = loadClass("io.akki.Log").getMethod("named", String::class.java).invoke(null, "discovered")
        val entry = loadClass("io.akki.LogContext").getConstructor(Map::class.java).newInstance(context)
            .let { it.javaClass.getMethod("enter").invoke(it) as AutoCloseable }
        entry.use {
            loadClass("io.akki.Logger").getMethod("info", String::class.java, Throwable::class.java, Map::class.java)
                .invoke(logger, "from isolated runtime", null, emptyMap<String, Any?>())
        }
    }

    private fun Path.declareProviders(vararg names: String) {
        val declarations = resolve(PROVIDER_DECLARATIONS)
        declarations.parent.createDirectories()
        declarations.writeText(names.joinToString("\n"))
    }

    private fun <T> withProviderProperty(provider: String, block: () -> T): T =
        withProperty(PROVIDER_PROPERTY, provider, block)

    private fun <T> withProperty(name: String, value: String, block: () -> T): T {
        val previous = System.setProperty(name, value)
        try {
            return block()
        } finally {
            if (previous == null) {
                System.clearProperty(name)
            } else {
                System.setProperty(name, previous)
            }
        }
    }
}

class StderrBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding =
        LoggerBinding { Sink { message, _, _ -> System.err.println("AKKI-DECLARED $loggerName - $message") } }
}

private class UndeclaredProvidersLoader(urls: Array<URL>) : URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
    override fun getResources(name: String): Enumeration<URL> =
        if (name == PROVIDER_DECLARATIONS) Collections.emptyEnumeration() else super.getResources(name)
}

private const val PROVIDER_DECLARATIONS: String = "META-INF/services/org.slf4j.spi.SLF4JServiceProvider"
private const val PROVIDER_PROPERTY: String = "slf4j.provider"
private const val BACKEND_PROPERTY: String = "io.akki.backend"
private const val INITIALIZER: String = "slf4j-initializer"
