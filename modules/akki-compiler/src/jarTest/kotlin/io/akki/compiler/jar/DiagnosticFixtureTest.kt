package io.akki.compiler.jar

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class DiagnosticFixtureTest {
    @Test
    fun `recognizes every directive of the diagnostics fixtures`() {
        val unknown =
            diagnosticFixtures.associate { it.path to it.unknownDirectives }.filterValues(Set<String>::isNotEmpty)

        assertEquals(emptyMap(), unknown)
    }

    @Test
    fun `finds the diagnostics that the plugin declares in its jar`() {
        val declared = setOf(
            "AKKI_CANNOT_START",
            "BLANK_LOGGER_NAME",
            "CONTEXTUAL_LOGGER_REFERENCE",
            "INCOMPATIBLE_AKKI_CORE",
            "LOG_AS_INITIALIZER",
            "LOGGING_CALL_REFERENCE",
            "LOGGING_CALL_REMOVED",
        )

        assertEquals(declared, pluginDiagnostics)
    }

    @ParameterizedTest(name = "Kotlin {0}: {1}")
    @MethodSource("fixtures")
    fun `reports the diagnostics that the fixture marks in a process of the compiler`(
        kotlin: String,
        fixture: Fixture,
    ) {
        val compilation = compiled(kotlin).getValue(fixture.path)

        assertContains(listOf("OK", "COMPILATION_ERROR"), compilation.exitCode, compilation.output)
        assertEquals(fixture.markedDiagnostics, fixture.reportedDiagnostics(compilation.output), compilation.output)
    }

    companion object {
        @TempDir(cleanup = CleanupMode.ON_SUCCESS)
        @JvmStatic
        lateinit var workspace: Path

        private val portable: List<Fixture> = diagnosticFixtures.filter(Fixture::isPortable)

        private val compiled = ConcurrentHashMap<String, Map<String, Compilation>>()

        @JvmStatic
        fun fixtures(): List<Arguments> =
            checkedKotlin.flatMap { kotlin -> portable.map { Arguments.of(kotlin, it) } }

        private fun compiled(kotlin: String): Map<String, Compilation> = compiled.computeIfAbsent(kotlin) {
            val process = CompilerProcess(kotlin, workspace.resolve(kotlin))
            portable.associate { fixture ->
                val name = fixture.path.removeSuffix(".kt")
                val options = fixture.compilerOptions()
                fixture.path to process.compile(name, fixture.unmarkedSources, fixture.runtime, *options)
            }.also { process.run() }
        }
    }
}
