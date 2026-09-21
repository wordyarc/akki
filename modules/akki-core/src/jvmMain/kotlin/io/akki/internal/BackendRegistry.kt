package io.akki.internal

import io.akki.Log
import io.akki.backend.LogBackend
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private object JvmBackendRegistry {
    private val backend: AtomicReference<BackendState> = AtomicReference(BackendState(discover()))

    fun backend(): LogBackend = backend.get().backend

    fun install(backend: LogBackend): Log.Installation {
        val installed = BackendState(backend)
        val previous = this.backend.getAndSet(installed)
        val active = AtomicBoolean(true)
        return Log.Installation {
            if (active.compareAndSet(true, false)) {
                if (!this.backend.compareAndSet(installed, previous)) {
                    active.set(true)
                    throw IllegalStateException("akki: backend installations must be uninstalled in reverse order")
                }
            }
        }
    }

    private class BackendState(val backend: LogBackend)
}

private fun discover(): LogBackend =
    try {
        chooseBackend(ServiceLoader.load(LogBackend::class.java, LogBackend::class.java.classLoader).toList())
    } catch (error: ServiceConfigurationError) {
        printError("akki: ignoring a broken backend service declaration: ${error.message}")
        DefaultBackend()
    }

internal fun chooseBackend(declared: List<LogBackend>): LogBackend {
    if (declared.isEmpty()) return DefaultBackend()
    val ordered = declared.sortedBy { it::class.java.name }
    val chosen = ordered.first()
    if (ordered.size > 1) {
        printError(
            ordered.joinToString(
                prefix = "akki: multiple backends on the classpath, using ${chosen::class.java.name}: ",
            ) { it::class.java.name },
        )
    }
    return chosen
}

internal actual fun platformBackend(): LogBackend = JvmBackendRegistry.backend()

internal actual fun installPlatformBackend(backend: LogBackend): Log.Installation = JvmBackendRegistry.install(backend)
