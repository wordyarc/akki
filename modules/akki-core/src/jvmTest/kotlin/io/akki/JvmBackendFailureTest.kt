package io.akki

import io.akki.backend.LogBackend
import io.akki.test.RecordingBackend
import io.akki.test.withBackend
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(DelicateAkkiApi::class)
class JvmBackendFailureTest {
    @Test
    fun `a backend failure is announced once per logger`(): Unit {
        val logger = Log.named("failure.announced")

        val output = captureStderr {
            withBackend(LogBackend { name -> error("backend is broken for $name") }) {
                repeat(3) { logger.info("dropped") }
            }
        }

        assertEquals(
            1,
            output.lineSequence().count {
                it.startsWith("akki: the backend failed to resolve logger 'failure.announced'")
            },
        )
        assertContains(output, "java.lang.IllegalStateException: backend is broken for failure.announced")
    }

    @Test
    fun `a backend failure is announced again for another backend`(): Unit {
        val logger = Log.named("failure.repeated")
        val broken = LogBackend { name -> error("backend is broken for $name") }

        val output = captureStderr {
            withBackend(broken) {
                logger.info("dropped")
                withBackend(RecordingBackend()) { logger.info("recorded") }
                logger.info("dropped again")
            }
            withBackend(LogBackend { name -> error("the next backend is broken for $name too") }) {
                logger.info("dropped elsewhere")
            }
        }

        assertEquals(
            2,
            output.lineSequence().count {
                it.startsWith("akki: the backend failed to resolve logger 'failure.repeated'")
            },
        )
        assertContains(output, "java.lang.IllegalStateException: backend is broken for failure.repeated")
        assertContains(output, "java.lang.IllegalStateException: the next backend is broken for failure.repeated too")
    }

    @Test
    fun `a linkage error in the backend is contained like any other failure`(): Unit {
        val logger = Log.named("failure.linkage")

        val output = captureStderr {
            withBackend(LogBackend { _ -> throw NoClassDefFoundError("org/slf4j/LoggerFactory") }) {
                logger.info("dropped")
                assertFalse(logger.isEnabled(Level.ERROR))
            }
        }

        assertEquals(
            1,
            output.lineSequence().count {
                it.startsWith("akki: the backend failed to resolve logger 'failure.linkage'")
            },
        )
        assertContains(output, "java.lang.NoClassDefFoundError: org/slf4j/LoggerFactory")
    }
}
