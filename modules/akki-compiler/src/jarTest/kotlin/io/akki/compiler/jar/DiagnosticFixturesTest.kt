package io.akki.compiler.jar

import kotlin.test.Test
import kotlin.test.assertFailsWith

internal class DiagnosticFixturesTest {
    @Test
    fun `requires successful compilation when no error is expected`() {
        val fixture = fixture("val value = 42")

        fixture.assertDiagnostics("OK", "warning: [UNUSED_VARIABLE] unused")
        assertFailsWith<AssertionError> { fixture.assertDiagnostics("COMPILATION_ERROR", "") }
    }

    @Test
    fun `requires the marked compiler error instead of another failure or success`() {
        val fixture = fixture("<!OVERRIDING_FINAL_MEMBER!>override<!> fun info() {}")
        val expected = "sample.kt:1:1: error: [OVERRIDING_FINAL_MEMBER] final member"

        fixture.assertDiagnostics("COMPILATION_ERROR", expected)
        assertFailsWith<AssertionError> { fixture.assertDiagnostics("OK", "") }
        assertFailsWith<AssertionError> {
            fixture.assertDiagnostics("COMPILATION_ERROR", "sample.kt:1:1: error: [UNRESOLVED_REFERENCE] missing")
        }
        assertFailsWith<AssertionError> {
            fixture.assertDiagnostics("COMPILATION_ERROR", "$expected\nerror: could not load a dependency")
        }
    }

    @Test
    fun `checks the default severity of plugin diagnostics`() {
        val fixture = fixture("val log = <!LOG_AS_INITIALIZER!>log<!>")
        val expected = "sample.kt:1:11: info: [LOG_AS_INITIALIZER] initializer"

        fixture.assertDiagnostics("OK", expected)
        assertFailsWith<AssertionError> { fixture.assertDiagnostics("OK", expected.replace("info:", "warning:")) }
        assertFailsWith<AssertionError> {
            fixture.assertDiagnostics("COMPILATION_ERROR", expected.replace("info:", "error:"))
        }
    }

    @Test
    fun `checks an explicit warning level and ignores unrelated compiler warnings`() {
        val fixture = fixture(
            "// WARNING_LEVEL: LOG_AS_INITIALIZER:warning\nval log = <!LOG_AS_INITIALIZER!>log<!>",
        )

        fixture.assertDiagnostics(
            "OK",
            "/tmp/sample.kt:2:11: warning: [LOG_AS_INITIALIZER] initializer\n" +
                "/tmp/sample.kt:2:1: warning: [UNUSED_VARIABLE] unused",
        )
    }

    @Test
    fun `requires the expected sourceless compiler error`() {
        val fixture = diagnosticFixtures.single { it.path == "diagnostics/loweredReference.kt" }

        fixture.assertDiagnostics("COMPILATION_ERROR", "error: [ERROR_SEVERITY_CHANGED] cannot disable an error")
        assertFailsWith<AssertionError> {
            fixture.assertDiagnostics("COMPILATION_ERROR", "error: invalid compiler argument")
        }
    }

    private fun fixture(source: String): Fixture = Fixture("sample.kt", source.lines())
}
