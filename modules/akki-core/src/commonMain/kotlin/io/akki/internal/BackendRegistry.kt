package io.akki.internal

import io.akki.Log
import io.akki.backend.LogBackend
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
internal object BackendRegistry {
    private val DISCOVERY_WAIT: Duration = 1.seconds

    private val initial = BackendState(
        DefaultBackend("akki: backend discovery is in progress, writing to stderr at INFO."),
    )
    private val current = AtomicReference(initial)
    private val discovery = AtomicReference<Discovery?>(null)
    private val reported = AtomicReference<LogBackend?>(null)

    fun backend(): LogBackend {
        val previous = current.load()
        if (previous !== initial) return previous.backend
        when (val state = discovery.load()) {
            null -> return runDiscovery()
            is Running -> state.await()
            is BackendState -> current.compareAndSet(initial, state)
        }
        return current.load().backend
    }

    fun install(backend: LogBackend): Log.Installation {
        val installed = BackendState(backend)
        val previous = current.exchange(installed)
        return Log.Installation(backend) {
            if (!current.compareAndSet(installed, previous)) {
                false
            } else {
                if (previous === initial) {
                    (discovery.load() as? BackendState)?.let { current.compareAndSet(initial, it) }
                }
                releasePlatformBackend(backend)
                reported.compareAndSet(backend, null)
                true
            }
        }
    }

    fun claimFailureReport(backend: LogBackend): Boolean {
        val previous = reported.load()
        return previous !== backend && reported.compareAndSet(previous, backend)
    }

    private fun runDiscovery(): LogBackend {
        val running = Running()
        if (!discovery.compareAndSet(null, running)) return backend()
        try {
            val discovered = BackendState(discoverPlatformBackend())
            discovery.store(discovered)
            current.compareAndSet(initial, discovered)
        } catch (failure: Throwable) {
            discovery.store(null)
            throw failure
        } finally {
            running.finish()
        }
        return current.load().backend
    }

    private sealed interface Discovery

    private class BackendState(val backend: LogBackend) : Discovery

    private class Running : Discovery {
        private val owner = currentThread()
        private val started = TimeSource.Monotonic.markNow()
        private val finished = Latch()

        fun await() {
            if (owner === currentThread()) return
            val remaining = DISCOVERY_WAIT - started.elapsedNow()
            if (remaining.isPositive()) finished.await(remaining)
        }

        fun finish() {
            finished.open()
        }
    }
}
