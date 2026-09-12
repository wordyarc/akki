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
        options: List<String> = emptyList(),
    ): Compilation {
        val compilation = run(fixture, plugin, classpath, options)
        assertEquals(ExitCode.OK, compilation.exitCode, compilation.output)
        return compilation
    }

    protected fun compileExpectingFailure(
        fixture: String,
        classpath: String = FixtureCompiler.defaultClasspath,
        options: List<String> = emptyList(),
    ): String {
        val compilation = run(fixture, Plugin.Enabled, classpath, options)
        assertNotEquals(ExitCode.OK, compilation.exitCode, compilation.output)
        return compilation.output
    }

    protected fun box(fixture: String, plugin: Plugin = Plugin.Enabled, options: List<String> = emptyList()): String =
        compile(fixture, plugin, options = options).invoke()

    protected fun assertBox(expected: String, fixture: String) {
        assertEquals(expected, box(fixture), fixture)
    }

    /**
     * Only for fixtures that never touch a contextual entry point: those compile and run without the plugin,
     * so plain Kotlin semantics stay available as a baseline.
     */
    protected fun assertLoweringIsTransparent(expected: String, fixture: String) {
        val plain = box(fixture, Plugin.Absent)
        assertEquals(expected, plain, "$fixture without the plugin")
        assertEquals(plain, box(fixture), "$fixture with the plugin")
    }

    private fun run(fixture: String, plugin: Plugin, classpath: String, options: List<String>): Compilation =
        FixtureCompiler.compile(
            workingDirectory.resolve(listOf(fixture, plugin.directory, *options.toTypedArray()).joinToString("-")),
            Fixtures.source(fixture),
            plugin,
            classpath,
            options,
        )
}

internal object Fixtures {
    fun source(name: String): String =
        requireNotNull(javaClass.getResource("/fixture/$name.kt")) { "no fixture /fixture/$name.kt" }.readText()
}
