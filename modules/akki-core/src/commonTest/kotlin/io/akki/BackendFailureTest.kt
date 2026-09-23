package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.test.RecordingBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(DelicateAkkiApi::class)
class BackendFailureTest {
    @Test
    fun `a backend that fails to resolve drops the record`(): Unit {
        val logger = Log.named("failure.resolving")

        Log.install(FailingBackend()).use {
            logger.info("dropped")
            logger.error("dropped too")

            assertNull(logger.sink(Level.INFO))
        }
    }

    @Test
    fun `a backend that fails to bind drops the record`(): Unit {
        val logger = Log.named("failure.binding")

        Log.install(FailingBackend(failing = Failing.BIND)).use {
            logger.warn("dropped")

            assertNull(logger.sink(Level.WARN))
        }
    }

    @Test
    fun `a level is disabled when the backend fails to answer`(): Unit {
        val logger = Log.named("failure.asking")

        Log.install(FailingBackend()).use {
            assertFalse(logger.isEnabled(Level.ERROR))
        }
    }

    @Test
    fun `a failed bind is not cached over a working backend`(): Unit {
        val logger = Log.named("failure.recovering")
        val working = RecordingBackend()

        Log.install(FailingBackend(failing = Failing.BIND)).use {
            logger.info("dropped")
        }
        Log.install(working).use {
            logger.info("recorded")
        }

        assertEquals(listOf("recorded"), working.records.map { it.message })
    }

    @Test
    fun `a failing backend is not asked again until another backend is installed`(): Unit {
        val logger = Log.named("failure.terminal")
        val backend = FailingBackend()
        val working = RecordingBackend()

        Log.install(backend).use {
            repeat(3) { logger.info("dropped") }
        }
        Log.install(working).use {
            logger.info("recorded")
        }

        assertEquals(1, backend.attempts)
        assertEquals(listOf("recorded"), working.records.map { it.message })
    }

    private enum class Failing {
        RESOLVE,
        BIND,
    }

    private class FailingBackend(private val failing: Failing = Failing.RESOLVE) : LogBackend {
        var attempts: Int = 0
            private set

        override fun bind(name: String): LoggerBinding {
            if (failing == Failing.BIND) fail(name)
            return LoggerBinding {
                attempts++
                if (failing == Failing.RESOLVE) fail(name)
                Sink { _, _, _ -> }
            }
        }

        private fun fail(name: String): Nothing = throw IllegalStateException("backend is broken for $name")
    }
}
