package io.akki.compiler

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class AkkiSymbolsTest : FixtureTest() {
    @Test
    fun `leaves modules without akki-core alone`() {
        assertEquals("plain", compile("withoutCore", classpath = "").invoke())
    }

    @Test
    fun `rejects an akki-core it cannot use`() {
        val core = compile("incompleteCore", Plugin.Absent, classpath = "")

        val failure = compileExpectingFailure("incompleteCoreUser", classpath = core.classes.toString())

        assertContains(failure, "error: ")
        assertContains(failure, "does not state its version")
        assertContains(failure, "must come from the same version")
        assertFalse(failure.contains("exception:"), failure)
    }

    @Test
    fun `names both versions when akki-core states another one`() {
        val stdlib = FixtureCompiler.defaultClasspath.split(File.pathSeparator).filter { "kotlin-stdlib" in it }
        val core = compile("incompleteCore", Plugin.Absent, classpath = "")
        val registry = compile("staleRegistry", Plugin.Absent, classpath = stdlib.joinToString(File.pathSeparator))
        val classpath = (listOf(core.classes, registry.classes).map { it.toString() } + stdlib)
            .joinToString(File.pathSeparator)

        val failure = compileExpectingFailure("incompleteCoreUser", classpath = classpath)

        assertContains(failure, "cannot use akki-core 0.0.1 on the compile classpath")
        assertContains(failure, "must come from the same version")
        assertFalse(failure.contains("does not declare"), failure)
    }
}
