package dev.ashenarx.akki

import dev.ashenarx.akki.internal.DefaultBackend
import dev.ashenarx.akki.test.withBackend
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JvmDefaultBackendTest {
    @Test
    fun defaultBackendWritesToStandardError(): Unit {
        val output = captureError {
            Log.named("acme.Checkout").info("started", fields = mapOf("orderId" to 42))
        }

        assertContains(output, "INFO  acme.Checkout - started {orderId=42}")
    }

    @Test
    fun defaultBackendAnnouncesItselfOnce(): Unit {
        val output = captureError {
            Log.named("acme.Notice").error("first")
            Log.named("acme.Notice").error("second")
        }

        assertEquals(1, output.lineSequence().count { it.startsWith("akki: no backend installed") })
    }

    @Test
    fun defaultBackendSuppressesLevelsBelowInfo(): Unit {
        val output = captureError {
            Log.named("acme.Quiet").trace("trace")
            Log.named("acme.Quiet").debug("debug")
        }

        assertFalse(output.contains("acme.Quiet"))
    }

    @Test
    fun defaultBackendKeepsCauseAttachedToItsMessage(): Unit {
        val output = captureError {
            Log.named("acme.Failing").error("failed", IllegalStateException("broken"))
        }

        val lines = output.lines()
        val message = lines.indexOfFirst { it.startsWith("ERROR acme.Failing - failed") }
        assertEquals("java.lang.IllegalStateException: broken", lines[message + 1])
    }

    private fun captureError(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(buffer, true))
        try {
            withBackend(DefaultBackend(), block)
        } finally {
            System.setErr(original)
        }
        return buffer.toString()
    }
}
