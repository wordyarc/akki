package io.akki.compiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class LogNameCheckerTest : FixtureTest() {
    @Test
    fun `rejects a blank logger name`() {
        val output = compileExpectingFailure("blankLogName")

        assertContains(output, "'@LogName' must name the logger of this declaration, but the name is blank")
    }

    @Test
    fun `reports the blank name once and leaves a real one alone`() {
        val output = compileExpectingFailure("blankLogName")

        assertEquals(1, output.lines().count { it.contains("the name is blank") }, output)
        assertFalse(output.contains("audit"), output)
    }
}
