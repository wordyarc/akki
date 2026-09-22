package io.akki

import io.akki.backend.LogBackend
import io.akki.test.withBackend
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(DelicateAkkiApi::class)
class JvmBackendFailureTest {
    @Test
    fun `a backend failure is announced once per logger`(): Unit {
        val logger = Log.named("failure.announced")

        val output = captureError {
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
    fun `a linkage error in the backend is contained like any other failure`(): Unit {
        val logger = Log.named("failure.linkage")

        val output = captureError {
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

    private fun captureError(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(buffer, true))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return buffer.toString()
    }
}
