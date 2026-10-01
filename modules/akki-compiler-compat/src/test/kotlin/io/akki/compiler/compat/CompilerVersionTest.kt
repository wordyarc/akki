package io.akki.compiler.compat

import io.akki.compiler.compat.CompilerVersion.Maturity
import kotlin.math.sign
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal class CompilerVersionTest {
    @Test
    fun `parses a release and its Beta and RC builds`() {
        assertEquals(CompilerVersion(2, 4, 20), CompilerVersion.parseOrNull("2.4.20"))
        assertEquals(CompilerVersion(2, 4, 20, Maturity.BETA), CompilerVersion.parseOrNull("2.4.20-Beta"))
        assertEquals(CompilerVersion(2, 4, 20, Maturity.BETA, 1), CompilerVersion.parseOrNull("2.4.20-Beta1"))
        assertEquals(CompilerVersion(2, 4, 20, Maturity.RC), CompilerVersion.parseOrNull("2.4.20-RC"))
        assertEquals(CompilerVersion(2, 4, 20, Maturity.RC, 2), CompilerVersion.parseOrNull("2.4.20-RC2"))
        assertEquals(CompilerVersion(0, 0, 0), CompilerVersion.parseOrNull("0.0.0"))
    }

    @Test
    fun `prints the version it parses`() {
        for (version in listOf("2.4.20", "2.4.20-Beta", "2.4.20-Beta1", "2.4.20-RC", "2.4.20-RC2", "0.0.0")) {
            assertEquals(version, CompilerVersion.parseOrNull(version).toString())
        }
    }

    @Test
    fun `rejects dev, IDE and snapshot builds and malformed versions`() {
        val rejected = listOf(
            "", "2", "2.4", "2.4.20.1", "2.4.", ".4.20", "2..20", "2.4.20+build", "2.4.x", "v2.4.20", "-2.4.20",
            "02.4.20", "2.04.20", "2.4.020", " 2.4.20", "2.4.20 ", "2.4.20\n", "2147483648.0.0", "2.4.99999999999",
            "2.4.20-dev-1234", "2.4.20-ij261-64", "2.4.20-SNAPSHOT", "2.4.20-M1", "2.4.20-beta1", "2.4.20-rc",
            "2.4.20-RC0", "2.4.20-Beta01", "2.4.20-RC-1", "2.4.20-Beta1-123", "2.4.20-RC-release-12", "2.4.20-",
            "2.4.20-RC99999999999",
        )

        for (version in rejected) assertNull(CompilerVersion.parseOrNull(version), "'$version'")
    }

    @Test
    fun `orders Beta and RC builds before their release and numbers by value`() {
        val ordered = listOf(
            "2.3.20-Beta1", "2.3.20-Beta2", "2.3.20-RC", "2.3.20-RC2", "2.3.20", "2.3.21-RC", "2.3.21",
            "2.4.0-Beta1", "2.4.0", "2.4.9", "2.4.10", "2.4.20-Beta9", "2.4.20-Beta10", "2.4.20", "2.10.0", "10.0.0",
        ).map { CompilerVersion.parseOrNull(it)!! }

        repeat(20) { seed -> assertEquals(ordered, ordered.shuffled(Random(seed)).sorted()) }
    }

    @Test
    fun `orders versions as KotlinToolingVersion does`() {
        val versions = listOf(
            "2.3.0-Beta1", "2.3.0-Beta2", "2.3.0-RC", "2.3.0-RC2", "2.3.0-RC3", "2.3.0", "2.3.10-RC", "2.3.10",
            "2.3.20-Beta1", "2.3.20-Beta2", "2.3.20-RC", "2.3.20-RC2", "2.3.20-RC3", "2.3.20", "2.3.21-RC", "2.3.21",
            "2.4.0-Beta1", "2.4.0-Beta2", "2.4.0-RC", "2.4.0-RC1", "2.4.0-RC2", "2.4.0", "2.4.10-RC", "2.4.10",
            "2.4.20-Beta", "2.4.20-Beta1", "2.4.20-Beta10", "2.4.20-RC", "2.4.20", "2.4.21-RC", "2.5.0-Beta1",
            "2.10.0", "10.0.0",
        )

        for (first in versions) {
            for (second in versions) {
                assertEquals(
                    KotlinToolingVersion(first).compareTo(KotlinToolingVersion(second)).sign,
                    CompilerVersion.parseOrNull(first)!!.compareTo(CompilerVersion.parseOrNull(second)!!).sign,
                    "$first <=> $second",
                )
            }
        }
    }

    @Test
    fun `rejects negative components and numbers out of a Beta or RC`() {
        assertFailsWith<IllegalArgumentException> { CompilerVersion(2, -1, 0) }
        assertFailsWith<IllegalArgumentException> { CompilerVersion(2, 4, 20, number = 1) }
        assertFailsWith<IllegalArgumentException> { CompilerVersion(2, 4, 20, Maturity.RC, 0) }
    }
}
