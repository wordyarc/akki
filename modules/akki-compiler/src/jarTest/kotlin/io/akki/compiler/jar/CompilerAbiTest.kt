package io.akki.compiler.jar

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `resolves every reference of the plugin in the embeddable compiler that Gradle runs`() {
        val host = classpath("akki.compiler.embeddable")

        assertContains(host.map { it.fileName.toString() }, "kotlin-compiler-embeddable-$latestTestedKotlin.jar")
        assertEquals(emptyList(), violations(adapterOf(latestTestedKotlin), host))
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
    fun `creates the delegate of every adapter but the oldest with the factory of the previous adapter`() {
        assertFalse(implementation(adapters.first()).delegates)
        for ((previous, adapter) in adapters.zipWithNext()) {
            val constructor = implementation(adapter).methodReferences.getValue("<init>()V")
            val factory = MemberReference(previous.factory.internalName, "<init>", "()V", false, false, false)

            assertContains(constructor, factory, adapter.toString())
        }
    }

    @Test
    fun `overrides in every adapter after the oldest exactly the operations that changed at its minimum`() {
        val changes = compatApiChanges(pluginClasses.getValue(CONTRACT))

        assertEquals(operations, changes.keys)
        for (adapter in adapters.drop(1)) {
            val overridden = operations.filterNot(implementation(adapter)::forwards).toSet()
            val changed = operations.filterTo(mutableSetOf()) { adapter.minVersion in changes.getValue(it) }

            assertEquals(changed, overridden, adapter.toString())
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
