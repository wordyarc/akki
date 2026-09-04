package dev.ashenarx.akki.compiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class AkkiSymbolsTest : FixtureTest() {
    @Test
    fun leavesModulesWithoutAkkiCoreAlone() {
        assertEquals("plain", compile("withoutCore", classpath = "").invoke())
    }

    @Test
    fun rejectsAnAkkiCoreItCannotUse() {
        val core = compile("incompleteCore", Plugin.Absent, classpath = "")

        val failure = compileExpectingFailure("incompleteCoreUser", classpath = core.classes.toString())

        assertContains(failure, "error: ")
        assertContains(failure, "must come from the same version")
        assertFalse(failure.contains("exception:"), failure)
    }
}
