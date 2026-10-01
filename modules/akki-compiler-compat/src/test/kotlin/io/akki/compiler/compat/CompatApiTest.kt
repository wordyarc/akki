package io.akki.compiler.compat

import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.config.KotlinCompilerVersion

internal class CompatApiTest {
    private val operations: List<Method> = CompilerCompat::class.java.declaredMethods.filterNot(Method::isSynthetic)

    @Test
    fun `names the compiler change behind every operation`() {
        assertEquals(emptyList(), operations.filter { it.api == null }.map(Method::getName))
        for (api in operations.mapNotNull { it.api }) {
            assertNotNull(CompilerVersion.parseCanonicalOrNull(api.since), "'${api.since}'")
            assertTrue(api.change.isNotBlank(), api.since)
        }
    }

    @Test
    fun `keeps no operation for a change that the oldest supported compiler already has`() {
        val compiler = assertNotNull(KotlinCompilerVersion.getVersion())
        val oldest = assertNotNull(CompilerVersion.parseCanonicalOrNull(compiler))
        val obsolete = operations.mapNotNull { operation ->
            val api = operation.api ?: return@mapNotNull null
            val changed = CompilerVersion.parseCanonicalOrNull(api.since) ?: return@mapNotNull null
            "${operation.name} since Kotlin ${api.since}: ${api.change}".takeIf { changed <= oldest }
        }

        assertEquals(emptyList(), obsolete)
    }

    private val Method.api: CompatApi?
        get() = getAnnotation(CompatApi::class.java)
}
