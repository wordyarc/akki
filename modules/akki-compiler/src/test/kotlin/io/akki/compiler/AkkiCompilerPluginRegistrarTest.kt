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
import kotlin.test.assertFails
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.config.messageCollector
import org.junit.jupiter.api.io.TempDir

internal class AkkiCompilerPluginRegistrarTest {
    @TempDir
    lateinit var directory: Path

    private val messages = RecordingCollector()

    @Test
    fun `registers the extensions through the adapter of the running compiler`() {
        val registered = register(AkkiCompilerPluginRegistrar(), messages)

        assertEquals(
            setOf(AkkiFirExtensionRegistrar::class.java.name, AkkiIrGenerationExtension::class.java.name),
            registered,
        )
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
        assertEquals(2, register(AkkiCompilerPluginRegistrar(), collector = null).size)
    }

    @Test
    fun `reports an error and registers nothing when no adapter can be loaded`() {
        val registered = register(isolatedRegistrar(), messages)

        assertEquals(emptySet(), registered)
        val (severity, failure) = messages.reported.single()
        assertEquals(CompilerMessageSeverity.ERROR, severity)
        assertEquals(
            "akki: compiler plugin $AKKI_VERSION cannot start. " +
                "No compiler adapter factories were found on the compiler plugin classpath.",
            failure,
        )
    }

    @Test
    fun `fails when no adapter can be loaded and the message collector discards errors`() {
        val failure = assertFails { register(isolatedRegistrar(), MessageCollector.NONE) }

        assertEquals(CompatLoadException::class.java.name, failure.javaClass.name)
        assertEquals(
            "akki: compiler plugin $AKKI_VERSION cannot start. " +
                "No compiler adapter factories were found on the compiler plugin classpath.",
            failure.message,
        )
    }

    @Test
    fun `registers nothing without a message collector when no adapter can be loaded`() {
        assertEquals(emptySet(), register(isolatedRegistrar(), collector = null))
    }

    @Test
    fun `reports a compiler that the selected adapter does not link against`() {
        val adapters = mapOf(
            UnlinkedFactory::class to "java.lang.NoSuchMethodError: registerExtension",
            MiscastFactory::class to "java.lang.ClassCastException: ProjectExtensionDescriptor",
        )

        for ((factory, cause) in adapters) {
            messages.clear()

            assertEquals(emptySet(), register(isolatedRegistrar(factory), messages))
            val (severity, failure) = messages.reported.last()
            assertEquals(CompilerMessageSeverity.ERROR, severity)
            assertEquals(
                "akki: compiler plugin $AKKI_VERSION cannot start. Kotlin ${KotlinCompilerVersion.getVersion()} is " +
                    "not supported: the compiler plugin does not link against it. Kotlin releases up to " +
                    "$LATEST_TESTED_KOTLIN are tested.\nCaused by: $cause",
                failure,
            )
        }
    }

    @Test
    fun `reports a compiler that the selected adapter cannot be created in`() {
        assertEquals(emptySet(), register(isolatedRegistrar(UnloadableFactory::class), messages))

        val (severity, failure) = messages.reported.single()
        assertEquals(CompilerMessageSeverity.ERROR, severity)
        val lines = failure.lines()
        assertEquals(3, lines.size, failure)
        val (headline, adapter, cause) = lines
        assertEquals(
            "akki: compiler plugin $AKKI_VERSION cannot start. Kotlin ${KotlinCompilerVersion.getVersion()} is " +
                "not supported: the compiler plugin does not link against it. Kotlin releases up to " +
                "$LATEST_TESTED_KOTLIN are tested.",
            headline,
        )
        assertTrue(
            adapter.startsWith(
                "Caused by: ${CompatLoadException::class.java.name}: The compiler adapter for Kotlin 2.3.20-Beta1 " +
                    "created by ${UnloadableFactory::class.java.name} from ",
            ),
            adapter,
        )
        assertTrue(adapter.endsWith("does not link against Kotlin ${KotlinCompilerVersion.getVersion()}."), adapter)
        assertEquals("Caused by: java.lang.NoClassDefFoundError: org/jetbrains/kotlin/Removed", cause)
    }

    private fun register(registrar: CompilerPluginRegistrar, collector: MessageCollector?): Set<String> {
        val configuration = CompilerConfiguration()
        if (collector != null) configuration.messageCollector = collector
        val storage = CompilerPluginRegistrar.ExtensionStorage()
        with(registrar) { storage.registerExtensions(configuration) }
        return storage.registeredExtensions.values.flatten().mapTo(mutableSetOf()) { it.javaClass.name }
    }

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
