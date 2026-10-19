package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.testing.allocatedBytes
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateAkkiApi::class)
class JvmHotPathAllocationTest {
    private val silent = LogBackend { LoggerBinding { null } }
    private val fields: Map<String, Any?> = mapOf("user" to 42)

    @Test
    fun `a suppressed record allocates nothing through the installed backend`(): Unit {
        Log.install(silent).use { assertNoAllocation(Log.named("hot.installed")) }
    }

    @Test
    fun `a suppressed record allocates nothing inside a scope`(): Unit {
        withLogScope(LogScope(silent)) { assertNoAllocation(Log.named("hot.scoped")) }
    }

    @Test
    fun `a suppressed record allocates nothing inside a context`(): Unit {
        Log.install(silent).use { withLogFields("user" to 42) { assertNoAllocation(Log.named("hot.context")) } }
    }

    private fun assertNoAllocation(logger: Logger) {
        val windows = List(WINDOWS) { allocatedBytes { repeat(ITERATIONS) { emit(logger, it) } } }

        assertEquals(0L, windows.last(), "allocated $windows bytes per window for ${logger.name}")
    }

    private fun emit(logger: Logger, attempt: Int) {
        logger.debug("constant")
        logger.debug { "lazy $attempt" }
        logger.debug("constant", fields = fields)
        logger.debug(fields = fields) { "lazy $attempt" }
    }

    private companion object {
        const val WINDOWS: Int = 2
        const val ITERATIONS: Int = 100_000
    }
}
