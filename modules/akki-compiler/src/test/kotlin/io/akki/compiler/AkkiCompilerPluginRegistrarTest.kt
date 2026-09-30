@file:OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class, MessageCollectorAccess::class)

package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import io.akki.compiler.compat.CompilerCompat
import io.akki.compiler.fir.AkkiFirExtensionRegistrar
import io.akki.compiler.ir.AkkiIrGenerationExtension
import java.net.URL
import java.util.Collections
import java.util.Enumeration
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

internal class AkkiCompilerPluginRegistrarTest {
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
        val registered = register(registrarWithoutAdapters(), messages)

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
    fun `fails when no adapter can be loaded and nothing collects messages`() {
        for (collector in listOf(null, MessageCollector.NONE)) {
            val failure = assertFails { register(registrarWithoutAdapters(), collector) }

            assertEquals(CompatLoadException::class.java.name, failure.javaClass.name)
            assertEquals("No compiler adapter factories were found on the compiler plugin classpath.", failure.message)
        }
    }

    private fun register(registrar: CompilerPluginRegistrar, collector: MessageCollector?): Set<String> {
        val configuration = CompilerConfiguration()
        if (collector != null) configuration.messageCollector = collector
        val storage = CompilerPluginRegistrar.ExtensionStorage()
        with(registrar) { storage.registerExtensions(configuration) }
        return storage.registeredExtensions.values.flatten().mapTo(mutableSetOf()) { it.javaClass.name }
    }

    private fun registrarWithoutAdapters(): CompilerPluginRegistrar {
        val registrar = PluginWithoutAdapters().loadClass(AkkiCompilerPluginRegistrar::class.java.name)
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

    private class PluginWithoutAdapters : ClassLoader(PluginWithoutAdapters::class.java.classLoader) {
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
            if (name == SERVICE) Collections.emptyEnumeration() else super.getResources(name)

        private companion object {
            const val PLUGIN_PACKAGE: String = "io.akki.compiler."

            val SERVICE: String = "META-INF/services/${CompilerCompat.Factory::class.java.name}"
        }
    }
}
