package io.akki.compiler.jar

private const val SOURCE_STYLE: String = "source"

private const val JVM_CLASS_STYLE: String = "jvm-class"

private val boxDeclaration = Regex("""^fun box\(\)""", RegexOption.MULTILINE)

private val packageDeclaration = Regex("""^package (\S+)""", RegexOption.MULTILINE)

internal val Fixture.box: String
    get() {
        val file = sources.values.single(boxDeclaration::containsMatchIn)
        return listOfNotNull(packageDeclaration.find(file)?.groupValues?.get(1), "box").joinToString(".")
    }

internal class BoxRun(val fixture: Fixture, val minLevel: String?, val style: String) {
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

internal val boxFixtures: List<Fixture> = fixtures("box", "names", "transparent", "semantics")

internal val boxRuns: List<BoxRun> = boxFixtures.filter(Fixture::isPortable).flatMap { fixture ->
    listOfNotNull(
        BoxRun(fixture, fixture.minLevel, SOURCE_STYLE),
        BoxRun(fixture, fixture.minLevel, JVM_CLASS_STYLE).takeIf { fixture.path.startsWith("names/") },
        BoxRun(fixture, "info", SOURCE_STYLE).takeIf { fixture.path.startsWith("semantics/") },
    )
}
