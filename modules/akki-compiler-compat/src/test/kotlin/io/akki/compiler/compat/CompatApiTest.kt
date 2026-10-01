package io.akki.compiler.compat

import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class CompatApiTest {
    private val operations: List<Method> = CompilerCompat::class.java.declaredMethods.filterNot(Method::isSynthetic)

    private val minimums: List<String> =
        requireNotNull(System.getProperty("akki.compiler.minimums")) { "missing -Dakki.compiler.minimums" }.split(',')

    private val oldest: CompilerVersion
        get() = minimums.map { assertNotNull(CompilerVersion.parseOrNull(it), it) }.min()

    @Test
    fun `recognizes every minimum in the order of the build`() {
        val versions = minimums.map { assertNotNull(CompilerVersion.parseOrNull(it), "'$it' is not recognized") }

        assertEquals(versions.sorted(), versions)
    }

    @Test
    fun `names the compiler change behind every operation`() {
        assertEquals(emptyList(), operations.filter { it.changes.isEmpty() }.map(Method::getName))
        for (api in operations.flatMap { it.changes }) assertTrue(api.change.isNotBlank(), api.since)
    }

    @Test
    fun `dates every change by the minimum of an adapter`() {
        assertEquals(emptyList(), operations.flatMap { it.changes }.map { it.since }.filterNot(minimums::contains))
        for (operation in operations) {
            val since = operation.changes.map { it.since }

            assertEquals(since.distinct(), since, operation.name)
        }
    }

    @Test
    fun `keeps no change that the oldest supported compiler already has`() {
        val obsolete = operations.flatMap { operation ->
            operation.changes.mapNotNull { api ->
                val changed = CompilerVersion.parseOrNull(api.since) ?: return@mapNotNull null
                "${operation.name} since Kotlin ${api.since}: ${api.change}".takeIf { changed <= oldest }
            }
        }

        assertEquals(emptyList(), obsolete)
    }

    @Test
    fun `keeps no adapter after the oldest without a change of the compiler API`() {
        val changes = operations.flatMap { it.changes }.mapTo(mutableSetOf()) { it.since }

        assertEquals(emptyList(), minimums.filterNot { it in changes || CompilerVersion.parseOrNull(it) == oldest })
    }

    private val Method.changes: List<CompatApi>
        get() = getAnnotationsByType(CompatApi::class.java).toList()
}
