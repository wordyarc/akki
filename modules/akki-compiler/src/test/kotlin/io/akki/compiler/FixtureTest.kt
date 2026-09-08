package io.akki.compiler

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.io.TempDir

internal abstract class FixtureTest {
    @TempDir
    protected lateinit var workingDirectory: Path

    protected fun compile(
        fixture: String,
        plugin: Plugin = Plugin.Enabled,
        classpath: String = FixtureCompiler.defaultClasspath,
    ): Compilation {
        val compilation = run(fixture, plugin, classpath)
        assertEquals(ExitCode.OK, compilation.exitCode, compilation.output)
        return compilation
    }

    protected fun compileExpectingFailure(
        fixture: String,
        classpath: String = FixtureCompiler.defaultClasspath,
    ): String {
        val compilation = run(fixture, Plugin.Enabled, classpath)
        assertNotEquals(ExitCode.OK, compilation.exitCode, compilation.output)
        return compilation.output
    }

    protected fun box(fixture: String, plugin: Plugin = Plugin.Enabled): String =
        compile(fixture, plugin).invoke()

    protected fun boxBothWays(fixture: String): Outputs =
        Outputs(lowered = box(fixture), plain = box(fixture, Plugin.Absent))

    protected fun assertLoweringIsTransparent(expected: String, fixture: String) {
        val outputs = boxBothWays(fixture)
        assertEquals(expected, outputs.plain, "$fixture without the plugin")
        assertEquals(outputs.plain, outputs.lowered, "$fixture with the plugin")
    }

    private fun run(fixture: String, plugin: Plugin, classpath: String): Compilation =
        FixtureCompiler.compile(
            workingDirectory.resolve("$fixture-${plugin.directory}"),
            Fixtures.source(fixture),
            plugin,
            classpath,
        )

    protected data class Outputs(val lowered: String, val plain: String)
}

internal object Fixtures {
    fun source(name: String): String =
        requireNotNull(javaClass.getResource("/fixture/$name.kt")) { "no fixture /fixture/$name.kt" }.readText()
}
