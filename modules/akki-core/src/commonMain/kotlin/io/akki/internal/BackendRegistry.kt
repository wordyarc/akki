package io.akki.internal

import io.akki.Log
import io.akki.backend.LogBackend
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal object BackendRegistry {
    private val initial = BackendState(
        DefaultBackend("akki: backend discovery is in progress, writing to stderr at INFO."),
    )
    private val current = AtomicReference(initial)
    private val discovery = AtomicReference<BackendState?>(null)
    private val reported = AtomicReference<LogBackend?>(null)

    fun backend(): LogBackend {
        val previous = current.load()
        if (previous !== initial || !discovery.compareAndSet(null, initial)) return previous.backend
        val discovered = try {
            BackendState(discoverPlatformBackend())
        } catch (failure: Throwable) {
            discovery.store(null)
            throw failure
        }
        discovery.store(discovered)
        current.compareAndSet(initial, discovered)
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
                    discovery.load()?.let { current.compareAndSet(initial, it) }
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

    private class BackendState(val backend: LogBackend)
}
