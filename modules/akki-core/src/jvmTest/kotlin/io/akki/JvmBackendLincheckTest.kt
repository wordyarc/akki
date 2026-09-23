package io.akki

import io.akki.test.RecordingBackend
import io.akki.test.withBackend
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.jetbrains.lincheck.Lincheck

class JvmBackendLincheckTest {
    @Test
    fun `a cached logger follows concurrent backend installation and restoration`() {
        Lincheck.runConcurrentTest(invocations = 1_000) {
            val original = RecordingBackend()
            val replacement = RecordingBackend()
            val logger = Log.named("lincheck.install")

            withBackend(original) {
                logger.info("before")
                val installer = thread {
                    withBackend(replacement) {
                        logger.info("installed")
                    }
                }
                val concurrent = Log.named("lincheck.install")
                assertSame(logger, concurrent)
                concurrent.info("concurrent")
                installer.join()
                logger.info("restored")
            }

            val before = original.records.map { it.message }
            val after = replacement.records.map { it.message }
            assertEquals(listOf("before", "restored"), before.filter { it != "concurrent" })
            assertEquals(listOf("installed"), after.filter { it != "concurrent" })
            assertEquals(1, (before + after).count { it == "concurrent" })
        }
    }
}
