package io.akki.compiler.jar

import java.nio.file.Path
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
        assertEquals(emptyList(), violations(plugin, host(kotlin)))
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `reports every reference into the compiler once the compiler is missing`(kotlin: String) {
        val plugin = pluginWith(adapterOf(kotlin))
        val libraries = host(kotlin).filterNot { it.fileName.toString().startsWith("kotlin-compiler-") }

        val violations = violations(plugin, libraries)

        val registrar = "io/akki/compiler/AkkiCompilerPluginRegistrar"
        assertContains(violations, "$registrar: org/jetbrains/kotlin/config/CompilerConfiguration is missing")
        assertContains(violations, "$registrar: org/jetbrains/kotlin/config/CommonConfigurationKeys is missing")
        assertTrue(violations.all { it.endsWith(" is missing") }, violations.toString())
        assertTrue(violations.none { it.startsWith("io/akki/compiler/compat/CompilerVersion") }, violations.toString())
    }

    private fun pluginWith(adapter: Adapter): Map<String, ByteArray> =
        classes.filterKeys { name -> adapters.none { it !== adapter && name in it && name !in factories } }

    private fun host(kotlin: String): List<Path> {
        val host = classpath("akki.compiler.host.$kotlin")
        val libraries = host.map { it.fileName.toString() }
        assertEquals(listOf("kotlin-compiler-$kotlin.jar"), libraries.filter { it.startsWith("kotlin-compiler-") })
        assertContains(libraries, "kotlin-stdlib-$kotlin.jar")
        return host
    }

    private fun violations(plugin: Map<String, ByteArray>, host: List<Path>): List<String> {
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
