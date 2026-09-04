package dev.ashenarx.akki.compiler

import dev.ashenarx.akki.compiler.FixtureCompiler.references
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class LoggerFieldLoweringTest {
    @Test
    fun derivesTheSameNamesAsTheFallback(@TempDir directory: Path) {
        val lowered = FixtureCompiler.box(directory.resolve("lowered"), MATRIX_FIXTURE, plugin = true)
        val plain = FixtureCompiler.box(directory.resolve("plain"), MATRIX_FIXTURE, plugin = false)
        assertEquals(plain, lowered)
    }

    @Test
    fun derivesTheSpecifiedNames(@TempDir directory: Path) {
        assertEquals(
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
            FixtureCompiler.box(directory, MATRIX_FIXTURE, plugin = true),
        )
    }

    @Test
    fun replacesTheStackWalkWithAStaticFieldRead(@TempDir directory: Path) {
        val lowered = FixtureCompiler.compile(directory.resolve("lowered"), INTRINSIC_FIXTURE, plugin = true)
        val plain = FixtureCompiler.compile(directory.resolve("plain"), INTRINSIC_FIXTURE, plugin = false)

        assertContains(lowered.references("fixture.Service"), "\$\$log")
        assertFalse(lowered.references("fixture.Service").any { it.contains("IntrinsicKt") })
        assertTrue(plain.references("fixture.Service").any { it.contains("IntrinsicKt") })
    }

    @Test
    fun createsOneFieldPerClassAndNotPerFile(@TempDir directory: Path) {
        val compilation = FixtureCompiler.compile(directory, TWO_CLASSES_FIXTURE, plugin = true)

        assertContains(compilation.references("fixture.First"), "\$\$log")
        assertContains(compilation.references("fixture.Second"), "\$\$log")
        assertFalse(compilation.classes.resolve("fixture/FixtureKt.class").exists())
    }

    @Test
    fun lowersInterfaceDefaultMethods(@TempDir directory: Path) {
        val lowered = FixtureCompiler.compile(directory.resolve("lowered"), INTERFACE_FIXTURE, plugin = true)
        val plain = FixtureCompiler.compile(directory.resolve("plain"), INTERFACE_FIXTURE, plugin = false)

        assertContains(lowered.references("fixture.Contract"), "\$\$log")
        assertFalse(lowered.references("fixture.Contract").any { it.contains("IntrinsicKt") })
        assertTrue(plain.references("fixture.Contract").any { it.contains("IntrinsicKt") })
    }

    @Test
    fun hidesTheFieldFromEveryoneButTheGeneratedCode(@TempDir directory: Path) {
        assertEquals("Contract=true,Service=true", FixtureCompiler.box(directory, INTERFACE_FIXTURE, plugin = true))
    }

    @Test
    fun leavesInlineFunctionsToTheFallback(@TempDir directory: Path) {
        val compilation = FixtureCompiler.compile(directory, INLINE_FIXTURE, plugin = true)

        assertTrue(compilation.references("fixture.FixtureKt").any { it.contains("IntrinsicKt") })
    }

    @Test
    fun resolvesTheLoggerBeforeTheStaticInitializersThatUseIt(@TempDir directory: Path) {
        val lowered = FixtureCompiler.box(directory.resolve("lowered"), STATIC_INIT_FIXTURE, plugin = true)
        val plain = FixtureCompiler.box(directory.resolve("plain"), STATIC_INIT_FIXTURE, plugin = false)

        assertEquals("fixture.Fixture,fixture.Holder", plain)
        assertEquals(plain, lowered)
    }

    private companion object {
        val INTERFACE_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            interface Contract {
                fun probe(): String = log.name
            }

            class Service {
                fun probe(): String = log.name
            }

            fun box(): String = listOf(Contract::class.java, Service::class.java).joinToString(",") {
                it.simpleName + "=" + it.declaredFields.single().isSynthetic
            }
            """.trimIndent()

        val MATRIX_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            class Service {
                class Nested {
                    fun probe(): String = log.name
                }

                inner class Inner {
                    fun probe(): String = log.name
                }

                object NestedObject {
                    fun probe(): String = log.name
                }

                companion object {
                    fun probe(): String = log.name
                }

                fun member(): String = log.name

                fun lambda(): String = listOf(0).map { log.name }.single()

                fun localClass(): String {
                    class Local {
                        fun probe(): String = log.name
                    }
                    return Local().probe()
                }

                fun objectExpression(): String {
                    val probe = object : Any() {
                        fun probe(): String = log.name
                    }
                    return probe.probe()
                }
            }

            class NamedCompanionHost {
                @LogName("companion-audit")
                companion object {
                    fun probe(): String = log.name
                }
            }

            object Standalone {
                fun probe(): String = log.name
            }

            enum class Colour {
                RED {
                    override fun probe(): String = log.name
                };

                abstract fun probe(): String
            }

            @LogName("class-audit")
            class Renamed {
                fun probe(): String = log.name
            }

            interface Contract {
                fun probe(): String = log.name
            }

            private class ContractImpl : Contract

            fun topLevel(): String = log.name

            fun box(): String = listOf(
                "topLevel=" + topLevel(),
                "member=" + Service().member(),
                "nested=" + Service.Nested().probe(),
                "inner=" + Service().Inner().probe(),
                "companion=" + Service.probe(),
                "namedCompanion=" + NamedCompanionHost.probe(),
                "standaloneObject=" + Standalone.probe(),
                "nestedObject=" + Service.NestedObject.probe(),
                "lambda=" + Service().lambda(),
                "localClass=" + Service().localClass(),
                "objectExpression=" + Service().objectExpression(),
                "enumEntry=" + Colour.RED.probe(),
                "renamedClass=" + Renamed().probe(),
                "interfaceMethod=" + ContractImpl().probe(),
            ).joinToString(",")
            """.trimIndent()

        val INTRINSIC_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            class Service {
                fun probe(): String = log.name
            }
            """.trimIndent()

        val TWO_CLASSES_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            class First {
                fun probe(): String = log.name
            }

            class Second {
                fun probe(): String = log.name
            }
            """.trimIndent()

        val INLINE_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            inline fun probe(): String = log.name
            """.trimIndent()

        val STATIC_INIT_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            private val topLevel: String = log.name

            object Holder {
                val member: String = log.name
            }

            fun box(): String = topLevel + "," + Holder.member
            """.trimIndent()
    }
}
