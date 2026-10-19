package io.akki

import io.akki.internal.chooseBackend
import io.akki.testing.captureStderr
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(DelicateAkkiApi::class)
class JvmDefaultBackendTest {
    @Test
    fun `default backend writes to standard error`(): Unit {
        val output = defaultBackendOutput {
            Log.named("acme.Checkout").info("started", fields = mapOf("orderId" to 42))
        }

        assertContains(output, "INFO  acme.Checkout - started {orderId=42}")
    }

    @Test
    fun `default backend writes the context before the fields`(): Unit {
        val output = defaultBackendOutput {
            withLogFields("requestId" to 7, "tenant" to "acme") {
                Log.named("acme.Context").info("started", fields = mapOf("orderId" to 42, "tenant" to "other"))
            }
        }

        assertContains(output, "INFO  acme.Context - started {requestId=7, orderId=42, tenant=other}")
    }

    @Test
    fun `default backend announces once which backend to add`(): Unit {
        val output = defaultBackendOutput {
            Log.named("acme.Notice").error("first")
            Log.named("acme.Notice").error("second")
        }

        assertEquals(1, output.lineSequence().count { it.startsWith("akki: no backend is available") })
        assertContains(output, "Add a backend such as akki-slf4j to the runtime classpath.")
    }

    @Test
    fun `default backend suppresses levels below INFO`(): Unit {
        val output = defaultBackendOutput {
            Log.named("acme.Quiet").trace("trace")
            Log.named("acme.Quiet").debug("debug")
        }

        assertFalse(output.contains("acme.Quiet"))
    }

    @Test
    fun `default backend keeps the cause attached to its message`(): Unit {
        val output = defaultBackendOutput {
            Log.named("acme.Failing").error("failed", IllegalStateException("broken"))
        }

        val lines = output.lines()
        val message = lines.indexOfFirst { it.startsWith("ERROR acme.Failing - failed") }
        assertEquals("java.lang.IllegalStateException: broken", lines[message + 1])
    }

    private fun defaultBackendOutput(block: () -> Unit): String = captureStderr {
        Log.install(chooseBackend(emptyList())).use { block() }
    }
}
