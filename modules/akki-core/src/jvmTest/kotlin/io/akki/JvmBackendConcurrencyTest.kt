package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.test.RecordingBackend
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(DelicateAkkiApi::class)
class JvmBackendConcurrencyTest {
    @Test
    fun `a late bind cannot revive a backend after a failed bind`(): Unit = lateBindAfterFailure(Lookup.BIND)

    @Test
    fun `a late bind cannot revive a backend after a failed resolve`(): Unit = lateBindAfterFailure(Lookup.RESOLVE)

    private fun lateBindAfterFailure(failureAt: Lookup) {
        val logger = Log.named("race.late-bind.$failureAt")
        val binds = AtomicInteger()
        val lateResolutions = AtomicInteger()

        PausedCall().use { paused ->
            val backend = LogBackend { _ ->
                if (binds.incrementAndGet() == 1) {
                    paused.pause()
                    LoggerBinding {
                        lateResolutions.incrementAndGet()
                        Sink { _, _, _ -> }
                    }
                } else {
                    when (failureAt) {
                        Lookup.BIND -> error("failed bind")
                        Lookup.RESOLVE -> LoggerBinding { error("failed resolve") }
                    }
                }
            }
            Log.install(backend).use {
                paused.start { logger.info("in flight") }
                captureStderr { logger.info("failure") }
                paused.finish()
                val resolved = lateResolutions.get()

                logger.info("after failure")

                assertFalse(logger.isEnabled(Level.INFO))
                assertEquals(resolved, lateResolutions.get())
                assertEquals(2, binds.get())
            }
        }
    }

    @Test
    fun `a late bind failure disables a concurrently published binding`() {
        val logger = Log.named("race.late-failure")
        val recording = RecordingBackend()
        val binds = AtomicInteger()

        PausedCall().use { paused ->
            val backend = LogBackend { name ->
                if (binds.incrementAndGet() == 1) {
                    paused.pause()
                    error("failed bind")
                }
                recording.bind(name)
            }
            Log.install(backend).use {
                paused.start { logger.info("in flight") }
                logger.info("before failure")
                captureStderr { paused.finish() }

                logger.info("after failure")

                assertFalse(logger.isEnabled(Level.INFO))
                assertEquals(listOf("before failure"), recording.records.map { it.message })
            }
        }
    }

    @Test
    fun `a late bind cannot evict the binding of a replacement backend`(): Unit = lateCallAfterReplacement(Lookup.BIND)

    @Test
    fun `a late resolve failure cannot evict the binding of a replacement backend`(): Unit =
        lateCallAfterReplacement(Lookup.RESOLVE)

    private fun lateCallAfterReplacement(pauseAt: Lookup) {
        val logger = Log.named("race.replaced.$pauseAt")
        val recording = RecordingBackend()
        val binds = AtomicInteger()
        val replacement = LogBackend { name ->
            binds.incrementAndGet()
            recording.bind(name)
        }

        PausedCall().use { paused ->
            val backend = LogBackend {
                when (pauseAt) {
                    Lookup.BIND -> {
                        paused.pause()
                        LoggerBinding { null }
                    }
                    Lookup.RESOLVE -> LoggerBinding {
                        paused.pause()
                        error("failed resolve")
                    }
                }
            }
            Log.install(backend).use {
                paused.start { logger.info("in flight") }
                Log.install(replacement).use {
                    logger.info("before completion")
                    captureStderr { paused.finish() }
                    logger.info("after completion")

                    assertEquals(listOf("before completion", "after completion"), recording.records.map { it.message })
                    assertEquals(1, binds.get())
                }
            }
        }
    }

    private enum class Lookup {
        BIND,
        RESOLVE,
    }

    private class PausedCall : AutoCloseable {
        private val executor = Executors.newSingleThreadExecutor()
        private val entered = CountDownLatch(1)
        private val released = CountDownLatch(1)
        private lateinit var task: Future<*>

        fun pause() {
            entered.countDown()
            released.await()
        }

        fun start(action: () -> Unit) {
            task = executor.submit { action() }
            assertTrue(entered.await(10, SECONDS), "backend did not reach the pause")
        }

        fun finish() {
            released.countDown()
            task.get(10, SECONDS)
        }

        override fun close() {
            released.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, SECONDS), "backend task did not finish")
        }
    }
}
