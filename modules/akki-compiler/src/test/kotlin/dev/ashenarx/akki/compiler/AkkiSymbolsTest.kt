package dev.ashenarx.akki.compiler

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
        assertContains(failure, "must come from the same version")
        assertFalse(failure.contains("exception:"), failure)
    }
}
