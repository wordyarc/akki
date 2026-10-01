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

    @Test
    fun `names the compiler change behind every operation`() {
        assertEquals(emptyList(), operations.filter { it.api == null }.map(Method::getName))
        for (api in operations.mapNotNull { it.api }) assertTrue(api.change.isNotBlank(), api.since)
    }

    @Test
    fun `dates every change by the minimum of an adapter`() {
        assertEquals(emptyList(), operations.mapNotNull { it.api?.since }.filterNot(minimums::contains))
    }

    @Test
    fun `keeps no operation for a change that the oldest supported compiler already has`() {
        val oldest = minimums.map { assertNotNull(CompilerVersion.parseOrNull(it), it) }.min()
        val obsolete = operations.mapNotNull { operation ->
            val api = operation.api ?: return@mapNotNull null
            val changed = CompilerVersion.parseOrNull(api.since) ?: return@mapNotNull null
            "${operation.name} since Kotlin ${api.since}: ${api.change}".takeIf { changed <= oldest }
        }

        assertEquals(emptyList(), obsolete)
    }

    private val Method.api: CompatApi?
        get() = getAnnotation(CompatApi::class.java)
}
