package dev.ashenarx.akki.compiler

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class ContextualLoggerCheckerTest {
    @Test
    fun warnsOnEveryContextualEntryPointInsideAnInlineFunction(@TempDir directory: Path) {
        val output = FixtureCompiler.compile(directory, INLINE_FIXTURE).output

        assertContains(output, "'log' inside the inline function 'viaIntrinsic'")
        assertContains(output, "'logger' inside the inline function 'viaAnchor'")
        assertContains(output, "'forCaller' inside the inline function 'viaFactory'")
        assertContains(output, "'log' inside the inline function 'withLambdaParameter'")
    }

    @Test
    fun staysSilentWhereTheLoggerIsResolvedAtTheDeclaration(@TempDir directory: Path) {
        val output = FixtureCompiler.compile(directory, QUIET_FIXTURE).output

        assertFalse(output.contains("inside the inline function"), output)
    }

    @Test
    fun warnsExactlyWhereTheFieldLoweringBailsOut(@TempDir directory: Path) {
        val output = FixtureCompiler.compile(directory, INLINE_FIXTURE).output

        assertEquals(
            listOf("viaIntrinsic", "viaAnchor", "viaFactory", "withLambdaParameter"),
            Regex("inside the inline function '(\\w+)'").findAll(output).map { it.groupValues[1] }.toList(),
        )
    }

    private companion object {
        val INLINE_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            inline fun viaIntrinsic(): String = log.name

            inline fun viaAnchor(): String = logger().name

            inline fun viaFactory(): String = Log.forCaller().name

            inline fun withLambdaParameter(body: () -> Unit): String {
                body()
                return log.name
            }
            """.trimIndent()

        val QUIET_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            fun topLevel(): String = log.name

            class Service {
                fun member(): String = log.name

                fun lambdaPassedToAnInlineFunction(): String = listOf(0).map { log.name }.single()
            }

            inline fun explicitLoggerIsFine(): String = Log.named("explicit").name
            """.trimIndent()
    }
}
