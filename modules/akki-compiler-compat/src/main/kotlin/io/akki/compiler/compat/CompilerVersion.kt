package io.akki.compiler.compat

internal data class CompilerVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val maturity: Maturity = Maturity.STABLE,
    val number: Int? = null,
) : Comparable<CompilerVersion> {
    enum class Maturity(val classifier: String?) {
        BETA("Beta"),
        RC("RC"),
        STABLE(null),
    }

    init {
        require(major >= 0 && minor >= 0 && patch >= 0)
        require(number == null || maturity != Maturity.STABLE && number > 0)
    }

    override fun compareTo(other: CompilerVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.maturity }, { it.number })

    override fun toString(): String =
        "$major.$minor.$patch" + maturity.classifier?.let { "-$it${number ?: ""}" }.orEmpty()

    companion object {
        private val pattern = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(Beta|RC)([1-9]\d*)?)?""")

        private val devBuild = Regex("""(.+)-dev-[1-9]\d*""")

        fun parseCompilerOrNull(value: String): CompilerVersion? {
            val release = devBuild.matchEntire(value)?.groupValues?.get(1) ?: return parseOrNull(value)
            return parseOrNull(release)?.takeIf { it.maturity == Maturity.STABLE }
        }

        fun parseOrNull(value: String): CompilerVersion? {
            val (major, minor, patch, classifier, number) = pattern.matchEntire(value)?.destructured ?: return null
            return CompilerVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
                maturity = Maturity.entries.single { it.classifier == classifier.ifEmpty { null } },
                number = if (number.isEmpty()) null else number.toIntOrNull() ?: return null,
            )
        }
    }
}
