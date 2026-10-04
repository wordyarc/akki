package io.akki.compiler.jar

import java.nio.file.Path
import kotlin.io.path.readText

private val testData: Path = classpath("akki.test.data").single()

private val fixtureClasspath: List<Path> = classpath("akki.fixture.classpath")

private val libraries: List<Path> = classpath("akki.fixture.libraries")

private val directive = Regex("""// ([A-Z][A-Z0-9_]*)(?::\s*(.*?))?\s*""")

private val supportedDirectives =
    setOf("FILE", "MIN_LEVEL", "WARNING_LEVEL", "WITH_HELPERS", "WITH_LOGBACK", "WITHOUT_AKKI")

private val inProcessDirectives = setOf(
    "CHECK_BYTECODE_TEXT",
    "CHECK_SOURCELESS_DIAGNOSTICS",
    "DUMP_KT_IR",
    "RENDER_IR_DIAGNOSTICS_FULL_TEXT",
    "RUN_PIPELINE_TILL",
    "TREAT_AS_ONE_FILE",
)

private val unsupportedDirectives = setOf("BACKEND_SERVICES", "MODULE", "WITHOUT_PLUGIN")

private val helpers = mapOf("WITH_HELPERS" to "Helpers.kt", "WITH_LOGBACK" to "Logback.kt")

internal class Fixture(val path: String, private val lines: List<String>) {
    private val directiveLines: Map<Int, MatchResult.Destructured> =
        lines.withIndex().mapNotNull { (index, line) -> directive.matchEntire(line)?.let { index to it.destructured } }
            .toMap()

    val directives: Map<String, List<String>> =
        directiveLines.values.groupBy({ (name, _) -> name }, { (_, value) -> value })

    val unknownDirectives: Set<String> =
        directives.keys - supportedDirectives - inProcessDirectives - unsupportedDirectives

    val isPortable: Boolean = unknownDirectives.isEmpty() && directives.keys.none(unsupportedDirectives::contains) &&
        directives["FILE"].orEmpty().all { it.endsWith(".kt") }

    val minLevel: String? = directives["MIN_LEVEL"]?.single()?.lowercase()

    val warningLevels: Map<String, String> = directives["WARNING_LEVEL"].orEmpty()
        .flatMap { it.split(',', ' ').filter(String::isNotEmpty) }
        .associate { it.substringBefore(':') to it.substringAfter(':') }

    val withoutAkki: Boolean = "WITHOUT_AKKI" in directives

    val runtime: List<Path>
        get() = if (withoutAkki) libraries else (fixtureClasspath + libraries).distinct()

    val sources: Map<String, String>
        get() = files() + helpers.filterKeys(directives::containsKey).values.associateWith { helper ->
            testData.resolve("helpers/$helper").readText()
        }

    private fun files(): Map<String, String> {
        val starts = directiveLines.filterValues { (name, _) -> name == "FILE" }.keys.sorted()
        if (starts.isEmpty()) return mapOf(path.substringAfterLast('/') to lines.joinToString("\n"))
        val bounds = listOf(0) + starts.drop(1) + lines.size
        return starts.indices.associate { index ->
            val (from, to) = bounds[index] to bounds[index + 1]
            val (_, name) = directiveLines.getValue(starts[index])
            name to "\n".repeat(from) + lines.subList(from, to).joinToString("\n")
        }
    }

    override fun toString(): String = path
}

internal fun fixtures(vararg directories: String): List<Fixture> = directories.flatMap { directory ->
    testData.resolve(directory).toFile().walk().filter { it.extension == "kt" }.sorted().map { file ->
        Fixture(file.relativeTo(testData.toFile()).invariantSeparatorsPath, file.readLines())
    }.toList()
}

internal fun Fixture.compilerOptions(minLevel: String? = this.minLevel): Array<String> =
    plugin + minLevel?.let { option("minLevel", it) }.orEmpty() +
        warningLevels.map { (name, level) -> "-Xwarning-level=$name:$level" }
