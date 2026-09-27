package io.akki.compiler

import io.akki.testing.BackgroundTask
import io.akki.testing.awaitSignal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class BackgroundTaskTest {
    @Test
    fun `await returns the background result`() {
        BackgroundTask("result") { 42 }.use { task ->
            assertEquals(42, task.await())
        }
    }

    @Test
    fun `await rethrows a background assertion on the test thread`() {
        val failure = AssertionError("background assertion")

        val caught = assertFailsWith<AssertionError> {
            BackgroundTask("failure") { throw failure }.use { it.await() }
        }

        assertSame(failure, caught)
    }

    @Test
    fun `a missing signal fails with the supplied diagnostic`() {
        val failure = assertFailsWith<AssertionError> {
            CountDownLatch(1).awaitSignal("discovery did not start", 20.milliseconds)
        }

        assertContains(failure.message.orEmpty(), "discovery did not start")
    }

    @Test
    fun `a timed out task is interrupted and stopped`() {
        val entered = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val task = BackgroundTask("blocked") {
            entered.countDown()
            try {
                CountDownLatch(1).awaitSignal("the task was not interrupted")
            } catch (failure: InterruptedException) {
                interrupted.set(true)
                throw failure
            }
        }

        val failure = assertFailsWith<AssertionError> {
            task.use {
                entered.awaitSignal("the task did not start")
                it.await(20.milliseconds)
            }
        }

        assertContains(failure.message.orEmpty(), "Background task 'blocked' did not finish within")
        assertIs<TimeoutException>(failure.cause)
        assertTrue(interrupted.get())
        assertEquals(Thread.State.TERMINATED, task.state)
    }

    @Test
    fun `a failing test still interrupts and stops its background task`() {
        val entered = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val failure = IllegalStateException("test failure")
        val task = BackgroundTask("cleanup") {
            entered.countDown()
            try {
                CountDownLatch(1).awaitSignal("the task was not interrupted")
            } catch (failure: InterruptedException) {
                interrupted.set(true)
                throw failure
            }
        }

        val caught = assertFailsWith<IllegalStateException> {
            task.use {
                entered.awaitSignal("the task did not start")
                throw failure
            }
        }

        assertSame(failure, caught)
        assertTrue(interrupted.get())
        assertEquals(Thread.State.TERMINATED, task.state)
    }

    @Test
    fun `an interrupted test still stops its background task and retains its interrupt`() {
        val entered = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val task = BackgroundTask("interrupted-cleanup") {
            entered.countDown()
            try {
                CountDownLatch(1).awaitSignal("the task was not interrupted")
            } catch (failure: InterruptedException) {
                interrupted.set(true)
                throw failure
            }
        }

        try {
            task.use {
                entered.awaitSignal("the task did not start")
                Thread.currentThread().interrupt()
            }

            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(interrupted.get())
            assertEquals(Thread.State.TERMINATED, task.state)
        } finally {
            Thread.interrupted()
        }
    }
}
