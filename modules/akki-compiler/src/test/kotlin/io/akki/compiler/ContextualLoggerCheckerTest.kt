package io.akki.compiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class ContextualLoggerCheckerTest : FixtureTest() {
    @Test
    fun `rejects every contextual entry point inside an inline declaration`() {
        val output = compileExpectingFailure("inlineDeclarations")

        assertContains(output, "'log' is not available inside the inline declaration 'viaIntrinsic'")
        assertContains(output, "'logger' is not available inside the inline declaration 'viaAnchor'")
        assertContains(output, "'forCaller' is not available inside the inline declaration 'viaFactory'")
        assertContains(output, "'log' is not available inside the inline declaration 'withLambdaParameter'")
        assertContains(output, "'log' is not available inside the inline declaration 'viaInlineProperty'")
        assertContains(output, "'log' is not available inside the inline declaration 'viaInlineGetter'")
    }

    @Test
    fun `stays silent where the logger is resolved at the declaration`() {
        val output = compile("resolvedAtDeclaration").output

        assertFalse(output.contains("inside the inline declaration"), output)
    }

    /**
     * A noinline lambda inside an inline function is a position the IR lowering refuses to touch, so the
     * checker has to reject it too: the two must agree on what the plugin leaves alone.
     */
    @Test
    fun `reports once per entry point, noinline lambdas included`() {
        val output = compileExpectingFailure("inlineDeclarations")

        assertEquals(
            listOf(
                "viaIntrinsic",
                "viaAnchor",
                "viaFactory",
                "withLambdaParameter",
                "viaInlineProperty",
                "viaInlineGetter",
                "withNoinlineDefault",
            ),
            Regex("inside the inline declaration '(\\w+)'").findAll(output).map { it.groupValues[1] }.toList(),
        )
    }
}
