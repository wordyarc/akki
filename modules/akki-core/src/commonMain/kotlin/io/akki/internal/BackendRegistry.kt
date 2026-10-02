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
    private val reportedBackend = AtomicReference<LogBackend?>(null)

    fun backend(): LogBackend {
        val state = current.load()
        if (state !== initial) return state.backend
        val discovered = discoverPlatformBackend() ?: return state.backend
        current.compareAndSet(initial, BackendState(discovered))
        return current.load().backend
    }

    fun install(backend: LogBackend): Log.Installation {
        val installed = BackendState(backend)
        val previous = current.exchange(installed)
        return Log.Installation(backend) {
            if (!current.compareAndSet(installed, previous)) {
                false
            } else {
                unbindLoggers(backend)
                reportedBackend.compareAndSet(backend, null)
                true
            }
        }
    }

    fun claimFailureReport(backend: LogBackend): Boolean {
        val reported = reportedBackend.load()
        return reported !== backend && reportedBackend.compareAndSet(reported, backend)
    }

    private class BackendState(val backend: LogBackend)
}
