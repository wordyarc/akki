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

internal class BoxFixtureTest {
    @Test
    fun `recognizes every directive of the box fixtures`() {
        val unknown = boxFixtures.associate { it.path to it.unknownDirectives }.filterValues(Set<String>::isNotEmpty)

        assertEquals(emptyMap(), unknown)
    }

    @ParameterizedTest(name = "Kotlin {0}: {1}")
    @MethodSource("boxRuns")
    fun `runs the box fixture with the plugin in a process of the compiler`(kotlin: String, run: BoxRun) {
        val compilation = compiled(kotlin).getValue(run.compilation)

        assertEquals("OK", compilation.exitCode, compilation.output)
        val output = compilation.run(BOX_MAIN_CLASS, run.fixture.runtime, "-D$LOGGER_NAME_STYLE=${run.style}")
        assertContains(output.lines(), "BOX OK", output)
    }

    companion object {
        @TempDir(cleanup = CleanupMode.ON_SUCCESS)
        @JvmStatic
        lateinit var workspace: Path

        private const val LOGGER_NAME_STYLE: String = "io.akki.loggerNameStyle"

        private const val BOX_MAIN_CLASS: String = "BoxMainKt"

        private val compiled = ConcurrentHashMap<String, Map<String, Compilation>>()

        @JvmStatic
        fun boxRuns(): List<Arguments> =
            checkedKotlin.flatMap { kotlin -> io.akki.compiler.jar.boxRuns.map { Arguments.of(kotlin, it) } }

        private fun boxMain(fixture: Fixture): Pair<String, String> =
            "BoxMain.kt" to "fun main() {\n    println(\"BOX \" + ${fixture.box}())\n}\n"

        private fun compiled(kotlin: String): Map<String, Compilation> = compiled.computeIfAbsent(kotlin) {
            val process = CompilerProcess(kotlin, workspace.resolve(kotlin))
            io.akki.compiler.jar.boxRuns.distinctBy(BoxRun::compilation).associate { run ->
                val options = run.fixture.compilerOptions(run.minLevel)
                val sources = run.fixture.sources + boxMain(run.fixture)
                run.compilation to process.compile(run.compilation, sources, run.fixture.runtime, *options)
            }.also { process.run() }
        }
    }
}
