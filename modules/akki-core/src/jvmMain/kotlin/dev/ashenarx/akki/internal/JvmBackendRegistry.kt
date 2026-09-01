package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Log
import dev.ashenarx.akki.LogBackend
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private object JvmBackendRegistry {
    private val backend: AtomicReference<BackendState> = AtomicReference(BackendState(DefaultBackend))

    fun backend(): LogBackend = backend.get().backend

    fun install(backend: LogBackend): Log.Installation {
        val installed = BackendState(backend)
        val previous = this.backend.getAndSet(installed)
        val active = AtomicBoolean(true)
        return Log.Installation {
            if (active.compareAndSet(true, false)) {
                if (!this.backend.compareAndSet(installed, previous)) {
                    active.set(true)
                    error("Backend installations must be uninstalled in reverse order")
                }
            }
        }
    }

    private class BackendState(val backend: LogBackend)
}

internal actual fun platformBackend(): LogBackend = JvmBackendRegistry.backend()

internal actual fun installPlatformBackend(backend: LogBackend): Log.Installation = JvmBackendRegistry.install(backend)
