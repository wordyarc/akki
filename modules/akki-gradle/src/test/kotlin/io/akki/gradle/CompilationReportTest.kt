package io.akki.gradle

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CompilationReportTest {
    @Test
    fun `collects every iteration within the requested task`() {
        val report = CompilationReport(
            """
            Compilation log for task ':library:compileKotlin':
              Compile iteration:
                library/src/Constants.kt
              Compile iteration:
                library/src/Constants.kt
                library/src/Service.kt <- dirty member library#CATEGORY
              Incremental compilation completed
            Time metrics:
              Incremental compilation in daemon: 0.1 s
            Task ':app:compileKotlin' finished in 0.1 s
            Compilation log for task ':app:compileKotlin':
              Compile iteration:
                app/src/Caller.kt <- dirty member library#traced
              Incremental compilation completed
            """.trimIndent(),
        )

        report.assertIncremental(":library:compileKotlin", "library/src/Constants.kt", "library/src/Service.kt")
        report.assertIncremental(":app:compileKotlin", "app/src/Caller.kt")
        assertFailsWith<AssertionError> {
            report.assertIncremental(":library:compileKotlin", "library/src/Constants.kt")
        }
    }

    @Test
    fun `rejects rebuilds and fallbacks even when no compiled sources are expected`() {
        val rebuilt = CompilationReport(
            """
            Compilation log for task ':library:compileKotlin':
              Non-incremental compilation will be performed: Unknown inputs changes
            Compilation log for task ':app:compileKotlin':
              Incremental compilation completed
            """.trimIndent(),
        )
        val fallback = CompilationReport(
            """
            Compilation log for task ':library:compileKotlin':
              Falling back to non-incremental compilation
            """.trimIndent(),
        )

        rebuilt.assertRebuild(":library:compileKotlin")
        rebuilt.assertIncremental(":app:compileKotlin")
        assertFailsWith<AssertionError> { rebuilt.assertIncremental(":library:compileKotlin") }
        assertFailsWith<AssertionError> { rebuilt.assertRebuild(":app:compileKotlin") }
        assertFailsWith<AssertionError> { fallback.assertIncremental(":library:compileKotlin") }
        assertFailsWith<AssertionError> { fallback.assertRebuild(":library:compileKotlin") }
    }

    @Test
    fun `requires an unambiguous compilation log for the requested task`() {
        val log = """
            Compilation log for task ':library:compileKotlin':
              Incremental compilation completed
        """.trimIndent()

        assertFailsWith<AssertionError> { CompilationReport("").assertIncremental(":library:compileKotlin") }
        assertFailsWith<AssertionError> { CompilationReport(log).assertIncremental(":app:compileKotlin") }
        assertFailsWith<AssertionError> { CompilationReport("$log\n$log").assertIncremental(":library:compileKotlin") }
    }
}
