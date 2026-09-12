package io.akki.compiler

import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class LoggerFieldLoweringTest : FixtureTest() {
    @Test
    fun `preserves the name of an annotated local class`() {
        assertBox(
            "local-audit,local-audit,local-audit,local-audit,local-audit",
            "namedLocalClass",
        )
    }

    @Test
    fun `evaluates contextual receivers and propagates their exceptions`() {
        assertBox(
            "selected,logger=receiver-audit,throwing,caught=true",
            "contextualReceiver",
        )
    }

    @Test
    fun `derives the specified names and agrees with the factory`() {
        assertBox(
            listOf(
                "topLevel=fixture.Fixture",
                "member=fixture.Service",
                "nested=fixture.Service.Nested",
                "inner=fixture.Service.Inner",
                "companion=fixture.Service",
                "namedCompanion=companion-audit",
                "standaloneObject=fixture.Standalone",
                "nestedObject=fixture.Service.NestedObject",
                "lambda=fixture.Service",
                "localClass=fixture.Service",
                "objectExpression=fixture.Service",
                "enumEntry=fixture.Colour",
                "renamedClass=class-audit",
                "interfaceMethod=fixture.Contract",
            ).joinToString(","),
            "nameMatrix",
        )
    }

    @Test
    fun `reads a static field holding a compile-time name instead of walking the stack`() {
        val lowered = compile("intrinsic").references("fixture.Service")
        val plain = compile("intrinsic", Plugin.Absent).references("fixture.Service")

        assertContains(lowered, "\$\$log")
        assertContains(lowered, "io/akki/internal/LogRegistry.forDeclaration")
        assertFalse(lowered.any { it.contains("forCaller") })
        assertTrue(plain.any { it.contains("IntrinsicKt") })
    }

    @Test
    fun `embeds both name styles so the runtime still picks between them`() {
        val nested = compile("nameMatrix").references("fixture.Service\$Nested")

        assertContains(nested, "fixture.Service.Nested")
        assertContains(nested, "fixture.Service\$Nested")
    }

    @Test
    fun `creates one field per class and not per file`() {
        val compilation = compile("twoClasses")

        assertContains(compilation.references("fixture.First"), "\$\$log")
        assertContains(compilation.references("fixture.Second"), "\$\$log")
        assertFalse(compilation.classes.resolve("fixture/FixtureKt.class").exists())
    }

    @Test
    fun `lowers interface default methods`() {
        val lowered = compile("interfaceDefault").references("fixture.Contract")
        val plain = compile("interfaceDefault", Plugin.Absent).references("fixture.Contract")

        assertContains(lowered, "\$\$log")
        assertFalse(lowered.any { it.contains("IntrinsicKt") })
        assertTrue(plain.any { it.contains("IntrinsicKt") })
    }

    @Test
    fun `hides the field from everyone but the generated code`() {
        assertEquals("Contract=true,Service=true", box("interfaceDefault"))
    }

    @Test
    fun `rejects the intrinsic in an inline function instead of lowering it`() {
        val output = compileExpectingFailure("inlineFunction")

        assertContains(output, "is not available inside the inline declaration")
    }

    @Test
    fun `leaves an explicit logger property alone and names it the same`() {
        assertBox(
            "fixture.Service,fixture.Service,fixture.Service,fixture.Holder,fixture.Fixture" +
                "|journal,INSTANCE+journal",
            "explicitLogger",
        )
    }

    @Test
    fun `resolves the logger before the static initializers that use it`() {
        assertBox("fixture.Fixture,fixture.Holder", "staticInitializer")
    }
}
