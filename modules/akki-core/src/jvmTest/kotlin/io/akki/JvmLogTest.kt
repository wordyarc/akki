package io.akki

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertSame

class JvmLogTest {
    @Test
    fun `javaClass factory matches KClass factory`(): Unit {
        assertSame(Log.of<JvmLogTest>(), Log.of(javaClass))
        assertEquals(JvmLogTest::class.java.name, Log.of(javaClass).name)
    }

    @Test
    fun `every contextual entry point reports the missing compiler plugin`(): Unit {
        val caller = Caller()
        val entryPoints = listOf<() -> Logger>(
            caller::intrinsic,
            caller::functionAnchor,
            ::fileIntrinsic,
            ::fileFunctionAnchor,
        )

        entryPoints.forEach { entryPoint ->
            val failure = assertFailsWith<IllegalStateException> { entryPoint() }

            assertContains(failure.message.orEmpty(), "the compiler plugin is not applied")
            assertContains(failure.message.orEmpty(), "Log.of<T>()")
        }
    }

    private class Caller {
        fun intrinsic(): Logger = log

        fun functionAnchor(): Logger = logger()
    }
}
