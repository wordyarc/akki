package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.test.RecordingBackend
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(DelicateAkkiApi::class)
class JvmBackendFailureTest {
    @Test
    fun `a backend failure is announced once for every logger it fails`(): Unit {
        val backend = LogBackend { name ->
            if (name.endsWith(".bind")) error("bind is broken for $name")
            LoggerBinding { error("resolve is broken for $name") }
        }

        val output = captureStderr {
            Log.install(backend).use {
                for (name in listOf("first.bind", "second.bind", "first.resolve", "second.resolve")) {
                    repeat(2) { Log.named("failure.announced.$name").info("dropped") }
                }
            }
        }

        assertEquals(1, output.lineSequence().count { it.startsWith("akki: the backend failed to resolve logger") })
        assertContains(output, "akki: the backend failed to resolve logger 'failure.announced.first.bind'")
        assertContains(output, "java.lang.IllegalStateException: bind is broken for failure.announced.first.bind")
    }

    @Test
    fun `a backend failure is announced again for another backend`(): Unit {
        val logger = Log.named("failure.repeated")
        val broken = LogBackend { name -> error("backend is broken for $name") }

        val output = captureStderr {
            Log.install(broken).use {
                logger.info("dropped")
                Log.install(RecordingBackend()).use { logger.info("recorded") }
                logger.info("dropped again")
            }
            Log.install(LogBackend { name -> error("the next backend is broken for $name too") }).use {
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
            Log.install(LogBackend { _ -> throw NoClassDefFoundError("org/slf4j/LoggerFactory") }).use {
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

    @Test
    fun `a fatal error from bind reaches the caller and the next record binds again`(): Unit {
        val logger = Log.named("failure.fatal.bind")
        val recording = RecordingBackend()
        var failures = 1
        val backend = LogBackend { name -> if (failures-- > 0) throw OutOfMemoryError() else recording.bind(name) }

        val output = captureStderr {
            Log.install(backend).use {
                assertFailsWith<OutOfMemoryError> { logger.info("lost") }
                logger.info("kept")
            }
        }

        assertEquals(listOf("kept"), recording.records.map { it.message })
        assertEquals("", output)
    }

    @Test
    fun `a fatal error from resolve reaches the caller and the logger keeps writing`(): Unit {
        val logger = Log.named("failure.fatal.resolve")
        val recording = RecordingBackend()
        var failures = 1
        val backend = LogBackend { name ->
            val binding = recording.bind(name)
            LoggerBinding { level -> if (failures-- > 0) throw StackOverflowError() else binding.resolve(level) }
        }

        val output = captureStderr {
            Log.install(backend).use {
                assertFailsWith<StackOverflowError> { logger.info("lost") }
                logger.info("kept")
            }
        }

        assertEquals(listOf("kept"), recording.records.map { it.message })
        assertEquals("", output)
    }
}
