package dev.ashenarx.akki.compiler

import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class LoggerFieldLoweringTest : FixtureTest() {
    @Test
    fun derivesTheSpecifiedNamesAndAgreesWithTheFallback() {
        assertLoweringIsTransparent(
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
    fun replacesTheStackWalkWithAStaticFieldRead() {
        val lowered = compile("intrinsic").references("fixture.Service")
        val plain = compile("intrinsic", Plugin.Absent).references("fixture.Service")

        assertContains(lowered, "\$\$log")
        assertFalse(lowered.any { it.contains("IntrinsicKt") })
        assertTrue(plain.any { it.contains("IntrinsicKt") })
    }

    @Test
    fun createsOneFieldPerClassAndNotPerFile() {
        val compilation = compile("twoClasses")

        assertContains(compilation.references("fixture.First"), "\$\$log")
        assertContains(compilation.references("fixture.Second"), "\$\$log")
        assertFalse(compilation.classes.resolve("fixture/FixtureKt.class").exists())
    }

    @Test
    fun lowersInterfaceDefaultMethods() {
        val lowered = compile("interfaceDefault").references("fixture.Contract")
        val plain = compile("interfaceDefault", Plugin.Absent).references("fixture.Contract")

        assertContains(lowered, "\$\$log")
        assertFalse(lowered.any { it.contains("IntrinsicKt") })
        assertTrue(plain.any { it.contains("IntrinsicKt") })
    }

    @Test
    fun hidesTheFieldFromEveryoneButTheGeneratedCode() {
        assertEquals("Contract=true,Service=true", box("interfaceDefault"))
    }

    @Test
    fun leavesInlineFunctionsToTheFallback() {
        val references = compile("inlineFunction").references("fixture.FixtureKt")

        assertTrue(references.any { it.contains("IntrinsicKt") })
    }

    @Test
    fun resolvesTheLoggerBeforeTheStaticInitializersThatUseIt() {
        assertLoweringIsTransparent("fixture.Fixture,fixture.Holder", "staticInitializer")
    }
}
