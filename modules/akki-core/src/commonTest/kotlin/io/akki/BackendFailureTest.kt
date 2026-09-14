package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.test.RecordingBackend
import io.akki.test.withBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(DelicateAkkiApi::class, InternalAkkiApi::class)
class BackendFailureTest {
    @Test
    fun `a backend that fails to resolve drops the record`(): Unit {
        val logger = Log.named("failure.resolving")

        withBackend(FailingBackend()) {
            logger.info("dropped")
            logger.emit(Level.ERROR, "dropped too")

            assertNull(logger.sink(Level.INFO))
        }
    }

    @Test
    fun `a backend that fails to bind drops the record`(): Unit {
        val logger = Log.named("failure.binding")

        withBackend(FailingBackend(failing = Failing.BIND)) {
            logger.warn("dropped")

            assertNull(logger.sink(Level.WARN))
        }
    }

    @Test
    fun `a level is disabled when the backend fails to answer`(): Unit {
        val logger = Log.named("failure.asking")

        withBackend(FailingBackend()) {
            assertFalse(logger.isEnabled(Level.ERROR))
        }
    }

    @Test
    fun `a failed bind is not cached over a working backend`(): Unit {
        val logger = Log.named("failure.recovering")
        val working = RecordingBackend()

        withBackend(FailingBackend(failing = Failing.BIND)) {
            logger.info("dropped")
        }
        withBackend(working) {
            logger.info("recorded")
        }

        assertEquals(listOf("recorded"), working.records.map { it.message })
    }

    @Test
    fun `a failing backend is asked again on the next record`(): Unit {
        val logger = Log.named("failure.repeating")
        val backend = FailingBackend()

        withBackend(backend) {
            repeat(3) { logger.info("dropped") }
        }

        assertTrue(backend.attempts >= 3, "resolve was tried ${backend.attempts} times")
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
