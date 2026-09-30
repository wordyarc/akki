package io.akki.compiler.compat

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal class CompilerVersionTest {
    @Test
    fun `parses a canonical version`() {
        assertEquals(CompilerVersion(2, 4, 20), CompilerVersion.parseCanonicalOrNull("2.4.20"))
        assertEquals(CompilerVersion(0, 0, 0), CompilerVersion.parseCanonicalOrNull("0.0.0"))
        assertEquals("2.4.20", CompilerVersion(2, 4, 20).toString())
    }

    @Test
    fun `rejects everything except three plain numbers`() {
        val rejected = listOf(
            "", "2", "2.4", "2.4.20.1", "2.4.", ".4.20", "2..20",
            "2.4.20-RC", "2.4.20-Beta1", "2.4.20-dev-1234", "2.4.20-ij261-64", "2.4.20+build", "2.4.20-SNAPSHOT",
            "v2.4.20", "02.4.20", "2.04.20", "2.4.020", " 2.4.20", "2.4.20 ", "2.4.20\n", "2.4.x", "-2.4.20",
            "2147483648.0.0", "2.4.99999999999",
        )

        for (version in rejected) assertNull(CompilerVersion.parseCanonicalOrNull(version), "'$version'")
    }

    @Test
    fun `orders versions by numbers and not by text`() {
        val ordered = listOf("2.3.20", "2.3.21", "2.4.0", "2.4.9", "2.4.10", "2.4.20", "2.10.0", "10.0.0")
            .map { CompilerVersion.parseCanonicalOrNull(it)!! }

        repeat(20) { seed -> assertEquals(ordered, ordered.shuffled(Random(seed)).sorted()) }
    }

    @Test
    fun `rejects negative components`() {
        assertFailsWith<IllegalArgumentException> { CompilerVersion(2, -1, 0) }
    }
}
