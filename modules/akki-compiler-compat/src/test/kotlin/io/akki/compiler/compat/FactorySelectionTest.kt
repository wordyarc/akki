package io.akki.compiler.compat

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

internal class FactorySelectionTest {
    private val k2320 = Minimum("2.3.20")
    private val k240 = Minimum("2.4.0")
    private val k2420 = Minimum("2.4.20")
    private val factories = listOf(k2320, k240, k2420)

    @Test
    fun `selects the factory whose minimum equals the compiler version`() {
        assertSame(k2320, select("2.3.20"))
        assertSame(k240, select("2.4.0"))
        assertSame(k2420, select("2.4.20"))
    }

    @Test
    fun `selects the nearest minimum below the compiler version`() {
        assertSame(k2320, select("2.3.21"))
        assertSame(k2320, select("2.3.99"))
        assertSame(k240, select("2.4.10"))
        assertSame(k240, select("2.4.19"))
        assertSame(k2420, select("2.4.21"))
        assertSame(k2420, select("3.0.0"))
    }

    @Test
    fun `compares minimums by numbers`() {
        val k249 = Minimum("2.4.9")
        val k2410 = Minimum("2.4.10")

        assertSame(k2410, select("2.4.10", listOf(k249, k2410)))
        assertSame(k2410, select("2.4.11", listOf(k2410, k249)))
    }

    @Test
    fun `does not depend on the discovery order`() {
        val versions = listOf("2.3.20", "2.3.21", "2.4.0", "2.4.10", "2.4.20", "2.5.0")
        val expected = versions.map { select(it) }

        repeat(20) { seed ->
            val shuffled = factories.shuffled(Random(seed))
            assertEquals(expected, versions.map { select(it, shuffled) })
        }
    }

    @Test
    fun `rejects a compiler older than every minimum`() {
        val failure = assertFailsWith<CompatLoadException> { select("2.3.10") }

        assertEquals("Kotlin 2.3.10 is not supported. The oldest supported version is 2.3.20.", failure.message)
    }

    @Test
    fun `rejects an empty set of factories`() {
        val failure = assertFailsWith<CompatLoadException> { select("2.4.20", emptyList()) }

        assertEquals("No compiler adapter factories were found on the compiler plugin classpath.", failure.message)
    }

    @Test
    fun `rejects a minimum that is not canonical`() {
        for (minimum in listOf("2.4", "2.4.0-RC", "2.4.0-dev-1", "latest", "")) {
            val failure = assertFailsWith<CompatLoadException> { select("2.4.20", factories + Minimum(minimum)) }

            assertContains(failure.message.orEmpty(), Minimum::class.java.name)
            assertContains(failure.message.orEmpty(), "declares an invalid minimum Kotlin version '$minimum'")
        }
    }

    @Test
    fun `rejects two factories with the same minimum`() {
        for (current in listOf("2.3.20", "2.4.0", "2.4.20")) {
            val failure = assertFailsWith<CompatLoadException> { select(current, factories + Rival("2.4.0")) }

            assertContains(failure.message.orEmpty(), "Several factories declare the minimum Kotlin version 2.4.0")
            assertContains(failure.message.orEmpty(), Minimum::class.java.name)
            assertContains(failure.message.orEmpty(), Rival::class.java.name)
        }
    }

    private fun select(current: String, from: List<CompilerCompat.Factory> = factories): CompilerCompat.Factory =
        selectFactory(CompilerVersion.parseCanonical(current)!!, from)

    private open class Minimum(override val minVersion: String) : CompilerCompat.Factory {
        override fun create(): CompilerCompat = error("selection must not create adapters")
    }

    private class Rival(minVersion: String) : Minimum(minVersion)
}
