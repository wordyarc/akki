package io.akki.compiler.jar

import kotlin.test.assertEquals

private val marker = Regex("""<!(?:([A-Z][A-Z0-9_]*(?:, [A-Z][A-Z0-9_]*)*)!)?>""")

private val report =
    Regex("""^(?:(?:.*[/\\])?([^/\\]+:\d+:\d+): )?(error|warning|info): (?:\[([A-Z][A-Z0-9_]*)] )?(.*)$""")

private const val DIAGNOSTICS_CONTAINER: String = "org/jetbrains/kotlin/diagnostics/KtDiagnosticsContainer"

private val factory = Regex("""get(\w+)\(\)Lorg/jetbrains/kotlin/diagnostics/Kt\w*DiagnosticFactory\w*;""")

internal val pluginDiagnostics: Set<String> = pluginShapes.values.filter { it.superName == DIAGNOSTICS_CONTAINER }
    .flatMap { it.members.keys }
    .mapNotNullTo(mutableSetOf()) { member -> factory.matchEntire(member)?.groupValues?.get(1) }

internal val pluginDiagnosticSeverities: Map<String, String> = mapOf(
    "AKKI_CANNOT_START" to "error",
    "BLANK_LOGGER_NAME" to "error",
    "CONTEXTUAL_LOGGER_REFERENCE" to "error",
    "INCOMPATIBLE_AKKI_CORE" to "error",
    "LOG_AS_INITIALIZER" to "info",
    "LOGGING_CALL_REFERENCE" to "error",
    "LOGGING_CALL_REMOVED" to "info",
)

private val compilerDiagnosticSeverities = mapOf(
    "NON_LOCAL_SUSPENSION_POINT" to "error",
    "NOTHING_TO_INLINE" to "warning",
    "OVERRIDING_FINAL_MEMBER" to "error",
)

internal val diagnosticFixtures: List<Fixture> = fixtures("diagnostics")

internal data class Diagnostic(val position: String?, val severity: String, val message: String) {
    override fun toString(): String = listOfNotNull(position, severity, message).joinToString(": ")
}

internal val Fixture.unmarkedSources: Map<String, String>
    get() = sources.mapValues { (_, text) -> marker.replace(text, "") }

internal val Fixture.expectedDiagnostics: List<Diagnostic>
    get() = buildList {
        for ((file, text) in sources) {
            text.lines().forEachIndexed { line, content ->
                var markup = 0
                for (match in marker.findAll(content)) {
                    val position = "$file:${line + 1}:${match.range.first - markup + 1}"
                    markup += match.value.length
                    for (name in match.groupValues[1].split(", ").filter(String::isNotEmpty)) {
                        val default = requireNotNull(pluginDiagnosticSeverities[name] ?: compilerDiagnosticSeverities[name]) {
                            "$path: the expected severity of $name is not declared"
                        }
                        val severity = warningLevels[name] ?: default
                        if (severity != "disabled") diagnostic(position, severity, name)?.let(::add)
                    }
                }
            }
        }
        if ("CHECK_SOURCELESS_DIAGNOSTICS" in directives) {
            val expected = requireNotNull(expectedOutput("sourceless.txt")) { "$path: missing sourceless expectations" }
            for (line in expected.lineSequence().filter(String::isNotBlank)) {
                val match = requireNotNull(report.matchEntire(line.substringAfter(": "))) {
                    "$path: unrecognized sourceless expectation: $line"
                }
                match.diagnostic()?.let(::add)
            }
        }
    }.sortedBy(Diagnostic::toString)

internal fun Fixture.assertDiagnostics(exitCode: String, output: String) {
    val expected = expectedDiagnostics
    val expectedExitCode = if (expected.any { it.severity == "error" }) "COMPILATION_ERROR" else "OK"

    assertEquals(expectedExitCode, exitCode, output)
    assertEquals(expected, reportedDiagnostics(output), output)
}

private fun reportedDiagnostics(output: String): List<Diagnostic> =
    output.lineSequence().mapNotNull { report.matchEntire(it)?.diagnostic() }.sortedBy(Diagnostic::toString).toList()

private fun MatchResult.diagnostic(): Diagnostic? {
    val (position, severity, name, message) = destructured
    return diagnostic(position.ifEmpty { null }, severity, name, message)
}

private fun diagnostic(position: String?, severity: String, name: String, message: String = ""): Diagnostic? {
    if (severity != "error" && name !in pluginDiagnostics) return null
    return Diagnostic(position, severity, if (name.isEmpty()) message else "[$name]")
}
