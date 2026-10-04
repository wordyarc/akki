package io.akki.compiler.jar

private val marker = Regex("""<!(?:([A-Z][A-Z0-9_]*(?:, [A-Z][A-Z0-9_]*)*)!)?>""")

private val report = Regex("""^(?:.*[/\\]([^/\\]+:\d+:\d+): )?(error|warning|info): \[([A-Z][A-Z0-9_]*)] """)

private const val DIAGNOSTICS_CONTAINER: String = "org/jetbrains/kotlin/diagnostics/KtDiagnosticsContainer"

private val factory = Regex("""get(\w+)\(\)Lorg/jetbrains/kotlin/diagnostics/Kt\w*DiagnosticFactory\w*;""")

internal val pluginDiagnostics: Set<String> = pluginShapes.values.filter { it.superName == DIAGNOSTICS_CONTAINER }
    .flatMap { it.members.keys }
    .mapNotNullTo(mutableSetOf()) { member -> factory.matchEntire(member)?.groupValues?.get(1) }

internal val diagnosticFixtures: List<Fixture> = fixtures("diagnostics")

internal val Fixture.unmarkedSources: Map<String, String>
    get() = sources.mapValues { (_, text) -> marker.replace(text, "") }

internal val Fixture.markedDiagnostics: List<String>
    get() = buildList {
        for ((file, text) in sources) {
            text.lines().forEachIndexed { line, content ->
                var markup = 0
                for (match in marker.findAll(content)) {
                    val position = "$file:${line + 1}:${match.range.first - markup + 1}"
                    markup += match.value.length
                    for (name in match.groupValues[1].split(", ")) {
                        if (name in pluginDiagnostics) add(diagnostic(position, warningLevels[name], name))
                    }
                }
            }
        }
    }.sorted()

internal fun Fixture.reportedDiagnostics(output: String): List<String> =
    output.lineSequence().mapNotNull { report.find(it)?.destructured }
        .filter { (_, _, name) -> name in pluginDiagnostics }
        .map { (position, severity, name) ->
            diagnostic(position.ifEmpty { null }, severity.takeIf { name in warningLevels }, name)
        }
        .sorted().toList()

private fun diagnostic(position: String?, severity: String?, name: String): String =
    listOfNotNull(position, severity, "[$name]").joinToString(": ")
