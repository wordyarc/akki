package io.akki.compiler.jar

import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class JarContentsTest {
    private val entries: List<Pair<String, ByteArray>> = compilerJar.entries()

    private val names: List<String> = entries.map { (name, _) -> name }

    @Test
    fun `stores every entry once`() {
        assertEquals(emptyList(), names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList())
    }

    @Test
    fun `declares the factory of every adapter in one descriptor`() {
        val expected = adapters.map { "${it.packageName}.CompilerCompatFactory" }.sorted()

        assertEquals(expected.joinToString(separator = "") { "$it\n" }, text(FACTORY_SERVICE))
    }

    @Test
    fun `contains the factory and the implementation of every adapter`() {
        for (provider in text(FACTORY_SERVICE).lines().filter(String::isNotEmpty)) assertContains(names, provider.file)
        for (adapter in adapters) assertContains(names, adapter.implementation.file)
    }

    @Test
    fun `declares one registrar and one command line processor`() {
        val plugin = "META-INF/services/org.jetbrains.kotlin.compiler.plugin"

        assertEquals("io.akki.compiler.AkkiCompilerPluginRegistrar\n", text("$plugin.CompilerPluginRegistrar"))
        assertEquals("io.akki.compiler.AkkiCommandLineProcessor\n", text("$plugin.CommandLineProcessor"))
        assertEquals(3, names.count { it.startsWith("META-INF/services/") }, names.toString())
    }

    @Test
    fun `contains the classes and the module metadata of akki only`() {
        val modules = listOf("io.akki.compiler", "io.akki.compiler.compat") + adapters.map(Adapter::packageName)
        val metadata = modules.map { "META-INF/$it.kotlin_module" } + "META-INF/MANIFEST.MF"
        val foreign = names.filterNot { name ->
            name in metadata || name.startsWith("META-INF/services/") ||
                name.startsWith("io/akki/compiler/") && name.endsWith(".class")
        }

        assertEquals(emptyList(), foreign)
        for (file in metadata) assertContains(names, file)
    }

    @Test
    fun `discovers the factories of the separate jars in the merged jar`() {
        val merged = factories(listOf(compilerJar))

        assertEquals(factories(classpath("akki.compiler.embedded")), merged)
        assertEquals(adapters.associate { it.minVersion to "${it.packageName}.CompilerCompatFactory" }, merged)
    }

    private fun factories(jars: List<Path>): Map<String, String> =
        URLClassLoader(jars.map { it.toUri().toURL() }.toTypedArray(), javaClass.classLoader).use { classLoader ->
            val service = classLoader.loadClass("io.akki.compiler.compat.CompilerCompat\$Factory")
            val minVersion = service.getMethod("getMinVersion")
            ServiceLoader.load(service, classLoader).associate { factory ->
                assertTrue(factory.javaClass.classLoader === classLoader, "${factory.javaClass} leaked into the tests")
                minVersion.invoke(factory) as String to factory.javaClass.name
            }
        }

    private fun text(name: String): String = entries.single { it.first == name }.second.decodeToString()

    private val String.file: String
        get() = "${replace('.', '/')}.class"
}
