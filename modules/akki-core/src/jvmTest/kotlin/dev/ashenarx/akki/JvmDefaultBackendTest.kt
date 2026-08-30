package dev.ashenarx.akki

import dev.ashenarx.akki.internal.DefaultBackend
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
        DefaultBackend.noticed = false

        val output = captureError {
            Log.named("acme.Notice").error("first")
            Log.named("acme.Notice").error("second")
        }

        assertEquals(
            1,
            output.lineSequence().count { it.startsWith("akki: no backend installed") },
        )
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
    fun defaultBackendPrintsCause(): Unit {
        val output = captureError {
            Log.named("acme.Failing").error("failed", IllegalStateException("broken"))
        }

        assertContains(output, "ERROR acme.Failing - failed")
        assertContains(output, "java.lang.IllegalStateException: broken")
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
