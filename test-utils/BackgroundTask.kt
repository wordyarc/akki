package io.akki.testing

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeoutException
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class BackgroundTask<T>(name: String, action: () -> T) : AutoCloseable {
    private val task = FutureTask { action() }
    private val thread = Thread(task, name).apply {
        isDaemon = true
        start()
    }

    val state: Thread.State get() = thread.state

    fun await(timeout: Duration = 10.seconds): T =
        try {
            task.get(timeout.timeoutNanos(), NANOSECONDS)
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } catch (failure: TimeoutException) {
            throw AssertionError("Background task '${thread.name}' did not finish within $timeout", failure)
        }

    override fun close() {
        task.cancel(true)
        val started = TimeSource.Monotonic.markNow()
        var interrupted = false
        try {
            while (thread.isAlive) {
                val remaining = 10.seconds - started.elapsedNow()
                assertTrue(
                    remaining.isPositive(),
                    "Background task '${thread.name}' did not stop after interruption: ${thread.state}",
                )
                try {
                    NANOSECONDS.timedJoin(thread, remaining.inWholeNanoseconds)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}

internal fun CountDownLatch.awaitSignal(message: String, timeout: Duration = 10.seconds) {
    assertTrue(await(timeout.timeoutNanos(), NANOSECONDS), message)
}

private fun Duration.timeoutNanos(): Long {
    require(isPositive() && isFinite()) { "A test timeout must be positive and finite: $this" }
    return inWholeNanoseconds
}
