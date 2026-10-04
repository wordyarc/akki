package io.akki.compiler.compat

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class LinkageTest {
    @Test
    fun `treats a compiler API that does not resolve or verify as broken linkage`() {
        val failures = listOf(
            NoSuchMethodError("org.jetbrains.kotlin.fir.FirSession getSession()"),
            NoSuchFieldError("INSTANCE"),
            AbstractMethodError("check"),
            IncompatibleClassChangeError("Expected static method"),
            IllegalAccessError("tried to access method"),
            NoClassDefFoundError("org/jetbrains/kotlin/Removed"),
            VerifyError("Bad type on operand stack"),
            BootstrapMethodError("call site initialization exception"),
        )

        for (failure in failures) assertTrue(failure.breaksLinkage, failure.toString())
    }

    @Test
    fun `treats a missing compiler API in a static initializer as broken linkage`() {
        val failure = assertFailsWith<NoSuchMethodError> { UnlinkedInitializer.touch() }

        assertTrue(failure.breaksLinkage)
    }

    @Test
    fun `does not treat a failed static initializer as broken linkage`() {
        val failure = assertFailsWith<ExceptionInInitializerError> { FailingInitializer.touch() }

        assertFalse(failure.breaksLinkage)
    }

    @Test
    fun `does not treat a class left uninitialized by an earlier failure as broken linkage`() {
        assertFailsWith<NoSuchMethodError> { UninitializedAfterUnlinked.touch() }

        val failure = assertFailsWith<NoClassDefFoundError> { UninitializedAfterUnlinked.touch() }

        assertIs<ExceptionInInitializerError>(failure.cause)
        assertFalse(failure.breaksLinkage)
    }

    @Test
    fun `does not treat other failures as broken linkage`() {
        val failures = listOf(IllegalStateException("plugin bug"), ClassCastException("FirSession"), StackOverflowError())

        for (failure in failures) assertFalse(failure.breaksLinkage, failure.toString())
    }

    @Test
    fun `finds the broken linkage under the failures that wrap it`() {
        val missing = NoSuchMethodError("compiler API is missing")

        assertSame(missing, missing.linkageBreak)
        assertSame(missing, IllegalStateException("lowering failed", RuntimeException(missing)).linkageBreak)
    }

    @Test
    fun `finds no broken linkage in a failure without one`() {
        val failed = ExceptionInInitializerError(IllegalStateException("plugin bug"))
        val uninitialized = NoClassDefFoundError("Could not initialize class Checker").apply { initCause(failed) }
        val failures = listOf(IllegalStateException("plugin bug"), failed, uninitialized)

        for (failure in failures) assertNull(RuntimeException(failure).linkageBreak, failure.toString())
    }

    private object UnlinkedInitializer {
        init {
            throw NoSuchMethodError("compiler API is missing")
        }

        fun touch() = Unit
    }

    private object FailingInitializer {
        init {
            throw IllegalStateException("plugin bug")
        }

        fun touch() = Unit
    }

    private object UninitializedAfterUnlinked {
        init {
            throw NoSuchMethodError("compiler API is missing")
        }

        fun touch() = Unit
    }
}
