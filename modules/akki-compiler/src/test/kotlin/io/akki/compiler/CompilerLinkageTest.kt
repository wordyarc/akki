package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import kotlin.test.Test
import kotlin.test.assertContains
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

    @Test
    fun `passes a failed static initializer and later uses of its class through`() {
        val failed = assertFailsWith<ExceptionInInitializerError> { linked { FailingInitializer.touch() } }
        val uninitialized = assertFailsWith<NoClassDefFoundError> { linked { FailingInitializer.touch() } }

        assertEquals("plugin bug", failed.cause?.message)
        assertContains(uninitialized.message.orEmpty(), FailingInitializer::class.java.name)
    }

    private object FailingInitializer {
        init {
            throw IllegalStateException("plugin bug")
        }

        fun touch() = Unit
    }
}
