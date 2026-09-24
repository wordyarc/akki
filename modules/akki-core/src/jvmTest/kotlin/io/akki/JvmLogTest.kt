package io.akki

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmLogTest {
    @Test
    fun `javaClass factory matches KClass factory`(): Unit {
        assertSame(Log.of<JvmLogTest>(), Log.of(javaClass))
        assertEquals(JvmLogTest::class.java.name, Log.of(javaClass).name)
    }

    @Test
    fun `concurrent first lookups of a name resolve one logger`(): Unit {
        val threads = 8
        val executor = Executors.newFixedThreadPool(threads) { task -> Thread(task).apply { isDaemon = true } }
        try {
            repeat(100) { round ->
                val name = "registry.race.$round"
                val start = CyclicBarrier(threads)
                val resolved = List(threads) {
                    executor.submit(
                        Callable {
                            start.await(10, SECONDS)
                            Log.named(name)
                        },
                    )
                }.map { it.get(10, SECONDS) }

                assertTrue(resolved.all { it === resolved.first() }, name)
            }
        } finally {
            executor.shutdownNow()
        }
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
