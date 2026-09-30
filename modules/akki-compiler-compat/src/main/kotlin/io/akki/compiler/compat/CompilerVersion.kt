package io.akki.compiler.compat

internal data class CompilerVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<CompilerVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0)
    }

    override fun compareTo(other: CompilerVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val canonical = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

        fun parseCanonicalOrNull(value: String): CompilerVersion? {
            val match = canonical.matchEntire(value) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            return CompilerVersion(major, minor, patch)
        }
    }
}
