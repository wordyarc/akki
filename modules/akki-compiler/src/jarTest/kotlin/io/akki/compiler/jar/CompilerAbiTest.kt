package io.akki.compiler.jar

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class CompilerAbiTest {
    private val classes: Map<String, ByteArray> = compilerJar.entries()
        .filter { (name, _) -> name.endsWith(".class") }
        .associate { (name, bytes) -> name.removeSuffix(".class") to bytes }

    private val factories: Set<String> = compilerJar.entries().single { it.first == FACTORY_SERVICE }.second
        .decodeToString().lines().filter(String::isNotEmpty).mapTo(mutableSetOf()) { it.replace('.', '/') }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `resolves every reference of the plugin with the selected adapter`(kotlin: String) {
        val plugin = pluginWith(adapterOf(kotlin))

        assertContains(plugin, adapterOf(kotlin).implementation.replace('.', '/'))
        assertTrue(plugin.keys.containsAll(factories), plugin.keys.toString())
        assertEquals(emptyList(), violations(kotlin, plugin))
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `finds the broken references of every other adapter`(kotlin: String) {
        for (adapter in adapters - adapterOf(kotlin)) {
            val violations = violations(kotlin, pluginWith(adapter))

            assertTrue(violations.isNotEmpty(), "$adapter links against Kotlin $kotlin")
            for (violation in violations) assertContains(violation, "${adapter.implementation.replace('.', '/')}: org/")
        }
    }

    private fun pluginWith(adapter: Adapter): Map<String, ByteArray> =
        classes.filterKeys { name -> adapters.none { it !== adapter && name in it && name !in factories } }

    private fun violations(kotlin: String, plugin: Map<String, ByteArray>): List<String> {
        val host = classpath("akki.compiler.host.$kotlin")
        val libraries = host.map { it.fileName.toString() }
        assertEquals(listOf("kotlin-compiler-$kotlin.jar"), libraries.filter { it.startsWith("kotlin-compiler-") })
        assertContains(libraries, "kotlin-stdlib-$kotlin.jar")
        val shapes = plugin.mapValues { (_, bytes) -> ClassShape.read(bytes, withReferences = true) }
        return CompilerAbi(shapes, host).use(CompilerAbi::violations)
    }

    private operator fun Adapter.contains(className: String): Boolean =
        className.startsWith("${packageName.replace('.', '/')}/")

    private companion object {
        @JvmStatic
        fun testedKotlin(): List<String> = io.akki.compiler.jar.testedKotlin
    }
}
