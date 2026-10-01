package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.jetbrains.kotlin.config.KotlinCompilerVersion

internal class CompilerLinkageTest {
    @Test
    fun `names the running compiler and the latest tested release when the plugin does not link`() {
        val missing = NoSuchMethodError("org.jetbrains.kotlin.fir.FirSession getSession()")

        val failure = assertFailsWith<CompatLoadException> { linked { throw missing } }

        assertEquals(
            "akki: compiler plugin $AKKI_VERSION failed. Kotlin ${KotlinCompilerVersion.getVersion()} is not " +
                "supported: the compiler plugin does not link against it. Kotlin releases up to " +
                "$LATEST_TESTED_KOTLIN are tested.",
            failure.message,
        )
        assertSame(missing, failure.cause)
    }

    @Test
    fun `passes results and other failures through`() {
        val bug = IllegalStateException("plugin bug")

        assertEquals("OK", linked { "OK" })
        assertSame(bug, assertFailsWith<IllegalStateException> { linked { throw bug } })
    }
}
