@file:OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class, MessageCollectorAccess::class)

package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import io.akki.compiler.compat.CompilerCompat
import io.akki.compiler.fir.AkkiFirExtensionRegistrar
import io.akki.compiler.ir.AkkiIrGenerationExtension
import java.net.URL
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import kotlin.io.path.writeLines
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.junit.jupiter.api.io.TempDir

internal class AkkiCompilerPluginRegistrarTest {
    @TempDir
    lateinit var directory: Path

    private val messages = RecordingCollector()

    @Test
    fun `registers the extensions and traces the adapter of the running compiler`() {
        val registration = register(AkkiCompilerPluginRegistrar(), messages)

        assertEquals(
            setOf(AkkiFirExtensionRegistrar::class.java.name, AkkiIrGenerationExtension::class.java.name),
            registration.extensions,
        )
        assertEquals(emptyList(), registration.diagnostics)
        val (severity, selection) = messages.reported.single()
        assertEquals(CompilerMessageSeverity.LOGGING, severity)
        assertContains(
            selection,
            "akki: compiler plugin $AKKI_VERSION starts. Kotlin ${KotlinCompilerVersion.getVersion()} uses",
        )
        assertContains(selection, "CompilerCompatFactory")
    }

    @Test
    fun `registers the extensions without a message collector`() {
        assertEquals(2, register(AkkiCompilerPluginRegistrar(), collector = null).extensions.size)
    }

    @Test
    fun `reports a failed start as a compiler error whatever the message collector`() {
        for (collector in listOf(messages, MessageCollector.NONE, null)) {
            messages.clear()

            val registration = register(isolatedRegistrar(), collector)

            assertEquals(emptySet(), registration.extensions)
            assertEquals(
                listOf(
                    "error: [AKKI_CANNOT_START] akki: compiler plugin $AKKI_VERSION cannot start. " +
                        "No compiler adapter factories were found on the compiler plugin classpath.",
                ),
                registration.diagnostics,
                collector.toString(),
            )
            assertEquals(emptyList(), messages.reported)
        }
    }

    @Test
    fun `reports a compiler that the selected adapter does not link against`() {
        val registration = register(isolatedRegistrar(UnloadableFactory::class), messages)

        assertEquals(emptySet(), registration.extensions)
        val lines = registration.diagnostics.single().lines()
        assertEquals(3, lines.size, lines.toString())
        val (headline, adapter, cause) = lines
        assertEquals(
            "error: [AKKI_CANNOT_START] akki: compiler plugin $AKKI_VERSION cannot start. " +
                "Kotlin ${KotlinCompilerVersion.getVersion()} is not supported: the compiler plugin does not link " +
                "against it. Kotlin releases up to $LATEST_TESTED_KOTLIN are tested.",
            headline,
        )
        assertTrue(
            adapter.startsWith(
                "Caused by: ${CompatLoadException::class.java.name}: The compiler adapter for Kotlin 2.4.0-Beta1 " +
                    "created by ${UnloadableFactory::class.java.name} from ",
            ),
            adapter,
        )
        assertTrue(adapter.endsWith("does not link against Kotlin ${KotlinCompilerVersion.getVersion()}."), adapter)
        assertEquals("Caused by: java.lang.NoClassDefFoundError: org/jetbrains/kotlin/Removed", cause)
    }

    @Test
    fun `rethrows a failed static initializer of the adapter instead of reporting a failed start`() {
        val configuration = CompilerConfiguration.create()

        val failure = assertFailsWith<ExceptionInInitializerError> {
            with(isolatedRegistrar(UninitializableFactory::class)) {
                CompilerPluginRegistrar.ExtensionStorage().registerExtensions(configuration)
            }
        }

        assertEquals("adapter initializer failed", failure.cause?.message)
        assertEquals(emptyList(), configuration.diagnosticsCollector.rendered())
    }

    private fun register(registrar: CompilerPluginRegistrar, collector: MessageCollector?): Registration {
        val configuration = CompilerConfiguration.create()
        if (collector != null) configuration.messageCollector = collector
        val storage = CompilerPluginRegistrar.ExtensionStorage()
        with(registrar) { storage.registerExtensions(configuration) }
        return Registration(
            extensions = storage.registeredExtensions.values.flatten().mapTo(mutableSetOf()) { it.javaClass.name },
            diagnostics = configuration.diagnosticsCollector.rendered(),
        )
    }

    private fun BaseDiagnosticsCollector.rendered(): List<String> = diagnosticsByFile[null].orEmpty().map {
        "${it.severity.name.lowercase()}: [${it.factoryName}] ${it.renderMessage()}"
    }

    private class Registration(val extensions: Set<String>, val diagnostics: List<String>)

    private fun isolatedRegistrar(vararg factories: KClass<out CompilerCompat.Factory>): CompilerPluginRegistrar {
        val providers = directory.resolve("providers").writeLines(factories.map { it.java.name })
        val registrar = IsolatedPlugin(providers.toUri().toURL()).loadClass(AkkiCompilerPluginRegistrar::class.java.name)
        assertTrue(registrar != AkkiCompilerPluginRegistrar::class.java)
        return registrar.getDeclaredConstructor().newInstance() as CompilerPluginRegistrar
    }

    private class RecordingCollector : MessageCollector {
        val reported = mutableListOf<Pair<CompilerMessageSeverity, String>>()

        override fun clear() = reported.clear()

        override fun hasErrors(): Boolean = reported.any { (severity, _) -> severity.isError }

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?,
        ) {
            reported += severity to message
        }
    }

    private class IsolatedPlugin(private val providers: URL) :
        ClassLoader(IsolatedPlugin::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith(PLUGIN_PACKAGE)) return super.loadClass(name, resolve)
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)?.let { return it }
                val bytes = parent.getResourceAsStream("${name.replace('.', '/')}.class")?.use { it.readBytes() }
                    ?: throw ClassNotFoundException(name)
                return defineClass(name, bytes, 0, bytes.size)
            }
        }

        override fun getResources(name: String): Enumeration<URL> =
            if (name == SERVICE) Collections.enumeration(listOf(providers)) else super.getResources(name)

        private companion object {
            const val PLUGIN_PACKAGE: String = "io.akki.compiler."

            val SERVICE: String = "META-INF/services/${CompilerCompat.Factory::class.java.name}"
        }
    }
}
