package io.akki.compiler.jar

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class CompilerAbiTest {
    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `resolves every reference of the plugin with the selected adapter`(kotlin: String) {
        val adapter = adapterOf(kotlin)
        val plugin = pluginWith(adapter)

        for (delegate in chain(adapter)) assertContains(plugin, delegate.implementation.internalName)
        assertTrue(plugin.containsAll(factories), plugin.toString())
        assertEquals(emptyList(), violations(adapter, host(kotlin)))
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `links every class of the plugin with the selected adapter`(kotlin: String) {
        assertEquals(emptyList(), linkageFailures(adapterOf(kotlin), host(kotlin)))
    }

    @Test
    fun `resolves and links the plugin in the embeddable compiler that Gradle runs`() {
        val host = classpath("akki.compiler.embeddable")
        val adapter = adapterOf(latestTestedKotlin)

        assertContains(host.map { it.fileName.toString() }, "kotlin-compiler-embeddable-$latestTestedKotlin.jar")
        assertEquals(emptyList(), violations(adapter, host))
        assertEquals(emptyList(), linkageFailures(adapter, host))
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `reports every reference into the compiler once the compiler is missing`(kotlin: String) {
        val libraries = host(kotlin).filterNot { it.fileName.toString().startsWith("kotlin-compiler-") }

        val violations = violations(adapterOf(kotlin), libraries)

        val registrar = "io/akki/compiler/AkkiCompilerPluginRegistrar"
        assertContains(violations, "$registrar: org/jetbrains/kotlin/config/CompilerConfiguration is missing")
        assertContains(violations, "${registrar}Kt: org/jetbrains/kotlin/config/CommonConfigurationKeys is missing")
        assertTrue(violations.all { it.endsWith(" is missing") }, violations.toString())
        assertTrue(violations.none { it.startsWith("io/akki/compiler/compat/CompilerVersion") }, violations.toString())
    }

    @Test
    fun `refers to no CliOption, whose constructor changes with every new parameter`() {
        val cliOption = "org/jetbrains/kotlin/compiler/plugin/CliOption"

        val referring = pluginShapes.values.filter { shape -> shape.references().any { it.owner == cliOption } }

        assertEquals(emptyList(), referring.map(ClassShape::name))
    }

    @Test
    fun `implements every operation that changed since the delegate or all operations without a delegate`() {
        val changes = compatApiChanges(pluginClasses.getValue(CONTRACT))

        assertEquals(operations, changes.keys)
        for (adapter in adapters) {
            val delegate = chain(adapter).getOrNull(1)
            val overridden = operations.filterNot(implementation(adapter)::forwards).toSet()
            val required = if (delegate == null) {
                operations
            } else {
                val minimums = adapters.subList(adapters.indexOf(delegate) + 1, adapters.indexOf(adapter) + 1)
                    .mapTo(mutableSetOf()) { it.minVersion }
                operations.filterTo(mutableSetOf()) { operation -> changes.getValue(operation).any(minimums::contains) }
            }

            assertTrue(overridden.containsAll(required), "$adapter must implement $required, but implements $overridden")
        }
    }

    private fun host(kotlin: String): List<Path> {
        val host = classpath("akki.compiler.host.$kotlin")
        val libraries = host.map { it.fileName.toString() }
        assertEquals(listOf("kotlin-compiler-$kotlin.jar"), libraries.filter { it.startsWith("kotlin-compiler-") })
        assertContains(libraries, "kotlin-stdlib-$kotlin.jar")
        return host
    }

    private companion object {
        @JvmStatic
        fun checkedKotlin(): List<String> = io.akki.compiler.jar.checkedKotlin
    }
}
