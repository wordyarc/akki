package dev.ashenarx.akki.compiler

import dev.ashenarx.akki.compiler.FixtureCompiler.invoke
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class AkkiSymbolsTest {
    @Test
    fun leavesModulesWithoutAkkiCoreAlone(@TempDir directory: Path) {
        val compilation = FixtureCompiler.compile(directory, WITHOUT_CORE_FIXTURE, classpath = "")

        assertEquals("plain", compilation.invoke("fixture.FixtureKt", "box"))
    }

    @Test
    fun rejectsAnAkkiCoreItCannotUse(@TempDir directory: Path) {
        val core = FixtureCompiler.compile(
            directory.resolve("core"),
            INCOMPLETE_CORE_FIXTURE,
            plugin = false,
            classpath = "",
        )

        val failure = FixtureCompiler.compileExpectingFailure(
            directory.resolve("user"),
            USER_FIXTURE,
            classpath = core.classes.toString(),
        )

        assertContains(failure, "must come from the same version")
    }

    private companion object {
        val WITHOUT_CORE_FIXTURE: String =
            """
            package fixture

            fun box(): String = "plain"
            """.trimIndent()

        val INCOMPLETE_CORE_FIXTURE: String =
            """
            package dev.ashenarx.akki

            interface Logger
            """.trimIndent()

        val USER_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.Logger

            fun box(logger: Logger): String = "unreachable"
            """.trimIndent()
    }
}
