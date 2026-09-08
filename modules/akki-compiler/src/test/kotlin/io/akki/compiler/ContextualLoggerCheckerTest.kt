package io.akki.compiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class ContextualLoggerCheckerTest : FixtureTest() {
    @Test
    fun `warns on every contextual entry point inside an inline function`() {
        val output = compile("inlineDeclarations").output

        assertContains(output, "'log' inside the inline declaration 'viaIntrinsic'")
        assertContains(output, "'logger' inside the inline declaration 'viaAnchor'")
        assertContains(output, "'forCaller' inside the inline declaration 'viaFactory'")
        assertContains(output, "'log' inside the inline declaration 'withLambdaParameter'")
        assertContains(output, "'log' inside the inline declaration 'viaInlineProperty'")
        assertContains(output, "'log' inside the inline declaration 'viaInlineGetter'")
    }

    @Test
    fun `stays silent where the logger is resolved at the declaration`() {
        val output = compile("resolvedAtDeclaration").output

        assertFalse(output.contains("inside the inline declaration"), output)
    }

    @Test
    fun `warns once per entry point and nowhere else`() {
        val output = compile("inlineDeclarations").output

        assertEquals(
            listOf(
                "viaIntrinsic",
                "viaAnchor",
                "viaFactory",
                "withLambdaParameter",
                "viaInlineProperty",
                "viaInlineGetter",
            ),
            Regex("inside the inline declaration '(\\w+)'").findAll(output).map { it.groupValues[1] }.toList(),
        )
    }
}
