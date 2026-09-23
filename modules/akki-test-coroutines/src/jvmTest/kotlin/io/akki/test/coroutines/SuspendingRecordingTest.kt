package io.akki.test.coroutines

import io.akki.Level
import io.akki.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class SuspendingRecordingTest {
    @Test
    fun `captures records across dispatchers`() = runTest {
        val logger = Log.named("suspending.dispatchers")

        val records = recordLogs {
            logger.info("here")
            withContext(Dispatchers.Default) { logger.info("default") }
            launch(Dispatchers.IO) { logger.info("child") }.join()
        }

        assertEquals(listOf("here", "default", "child"), records.map { it.message })
    }

    @Test
    fun `waits for children launched in the block`() = runTest {
        val logger = Log.named("suspending.children")

        val records = recordLogs {
            repeat(4) { child -> launch(Dispatchers.Default) { logger.info("child-$child") } }
        }

        assertEquals(List(4) { "child-$it" }.toSet(), records.map { it.message }.toSet())
    }

    @Test
    fun `nested captures record separately`() = runTest {
        val logger = Log.named("suspending.nested")

        val outer = recordLogs {
            val inner = recordLogs { withContext(Dispatchers.Default) { logger.info("inside") } }
            logger.info("outside")

            assertEquals(listOf("inside"), inner.map { it.message })
        }

        assertEquals(listOf("outside"), outer.map { it.message })
    }

    @Test
    fun `records below the requested level are not captured`() = runTest {
        val logger = Log.named("suspending.level")

        val records = recordLogs(Level.WARN) {
            logger.info("ignored")
            logger.error("kept")
        }

        assertEquals(listOf("kept"), records.map { it.message })
    }

    @Test
    fun `parallel captures see only their own records`() = runTest {
        val logger = Log.named("suspending.parallel")

        val captured = List(8) { writer ->
            async(Dispatchers.Default) {
                recordLogs {
                    repeat(100) {
                        logger.info("$writer-$it")
                        if (it % 10 == 0) yield()
                    }
                }
            }
        }.awaitAll()

        captured.forEachIndexed { writer, records ->
            assertEquals(List(100) { "$writer-$it" }, records.map { it.message })
        }
    }
}
