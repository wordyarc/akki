package io.akki.compiler

import kotlin.test.Test
import kotlin.test.assertEquals

internal class RedundantLoggerPropertyCheckerTest : FixtureTest() {
    @Test
    fun `points every shadowable alias back at the entry point it duplicates`() {
        val output = compile("redundantLogger").output

        assertEquals(
            listOf(
                "journal" to "logger",
                "fromIntrinsic" to "log",
                "fromFactory" to "forCaller",
                "here" to "logger",
            ),
            reported(output),
        )
    }

    @Test
    fun `keeps the property that the plugin does not replace`() {
        val output = compile("redundantLogger").output

        assertEquals(emptyList(), reported(output).filter { it.first == "runtimeType" || it.first == "named" })
    }

    private fun reported(output: String): List<Pair<String, String>> =
        Regex("'(\\w+)' holds the logger of the enclosing declaration, which is what '(\\w+)'")
            .findAll(output)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
}
