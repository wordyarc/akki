package dev.ashenarx.akki

import dev.ashenarx.akki.test.RecordingBackend
import dev.ashenarx.akki.test.messages
import dev.ashenarx.akki.test.withBackend
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

        withBackend(FailingBackend(failing = Failing.IS_ENABLED)) {
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

        assertEquals(listOf("recorded"), working.records.messages)
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
        IS_ENABLED,
    }

    private class FailingBackend(private val failing: Failing = Failing.RESOLVE) : LogBackend {
        var attempts: Int = 0
            private set

        override fun resolve(name: String, level: Level): Sink? {
            attempts++
            if (failing == Failing.RESOLVE) fail(name)
            return Sink { _, _, _ -> }
        }

        override fun isEnabled(name: String, level: Level): Boolean {
            if (failing == Failing.IS_ENABLED) fail(name)
            return true
        }

        override fun bind(name: String): SinkResolver {
            if (failing == Failing.BIND) fail(name)
            return SinkResolver { level -> resolve(name, level) }
        }

        private fun fail(name: String): Nothing = throw IllegalStateException("backend is broken for $name")
    }
}
