package io.akki.compiler.jar

import java.nio.file.Path
import kotlin.io.path.readText

private const val SOURCE_STYLE: String = "source"

private const val JVM_CLASS_STYLE: String = "jvm-class"

private val testData: Path = classpath("akki.test.data").single()

private val directive = Regex("""// ([A-Z][A-Z0-9_]*)(?::\s*(.*?))?\s*""")

private val boxDeclaration = Regex("""^fun box\(\)""", RegexOption.MULTILINE)

private val packageDeclaration = Regex("""^package (\S+)""", RegexOption.MULTILINE)

private val supportedDirectives = setOf("FILE", "MIN_LEVEL", "WITH_HELPERS", "WITH_LOGBACK", "WITHOUT_AKKI")

private val inProcessDirectives = setOf("CHECK_BYTECODE_TEXT", "DUMP_KT_IR", "TREAT_AS_ONE_FILE")

private val unsupportedDirectives = setOf("BACKEND_SERVICES", "MODULE", "WITHOUT_PLUGIN")

private val helpers = mapOf("WITH_HELPERS" to "Helpers.kt", "WITH_LOGBACK" to "Logback.kt")

internal class BoxFixture(val path: String, private val lines: List<String>) {
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

    val withoutAkki: Boolean = "WITHOUT_AKKI" in directives

    val sources: Map<String, String>
        get() = files() + helpers.filterKeys(directives::containsKey).values.associateWith { helper ->
            testData.resolve("helpers/$helper").readText()
        }

    val box: String
        get() {
            val file = files().values.single(boxDeclaration::containsMatchIn)
            return listOfNotNull(packageDeclaration.find(file)?.groupValues?.get(1), "box").joinToString(".")
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

internal class BoxRun(val fixture: BoxFixture, val minLevel: String?, val style: String) {
    val compilation: String
        get() = fixture.path.removeSuffix(".kt") + minLevel?.let { "-$it" }.orEmpty()

    override fun toString(): String {
        val variant = listOfNotNull(
            minLevel?.takeIf { it != fixture.minLevel }?.let { "minLevel=$it" },
            style.takeIf { it != SOURCE_STYLE },
        )
        return if (variant.isEmpty()) fixture.path else "${fixture.path} (${variant.joinToString()})"
    }
}

internal val boxFixtures: List<BoxFixture> = listOf("box", "names", "transparent", "semantics").flatMap { directory ->
    testData.resolve(directory).toFile().walk().filter { it.extension == "kt" }.sorted().map { file ->
        BoxFixture(file.relativeTo(testData.toFile()).invariantSeparatorsPath, file.readLines())
    }.toList()
}

internal val boxRuns: List<BoxRun> = boxFixtures.filter(BoxFixture::isPortable).flatMap { fixture ->
    listOfNotNull(
        BoxRun(fixture, fixture.minLevel, SOURCE_STYLE),
        BoxRun(fixture, fixture.minLevel, JVM_CLASS_STYLE).takeIf { fixture.path.startsWith("names/") },
        BoxRun(fixture, "info", SOURCE_STYLE).takeIf { fixture.path.startsWith("semantics/") },
    )
}
