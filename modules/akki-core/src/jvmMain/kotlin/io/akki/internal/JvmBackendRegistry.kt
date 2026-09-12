package io.akki.internal

import io.akki.AkkiException
import io.akki.Log
import io.akki.LogBackend
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
                    throw AkkiException("akki: backend installations must be uninstalled in reverse order")
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
    val chosen = declared.firstOrNull() ?: return DefaultBackend()
    if (declared.size > 1) {
        printError(
            declared.joinToString(
                prefix = "akki: multiple backends on the classpath, using ${chosen::class.java.name}: ",
            ) { it::class.java.name },
        )
    }
    return chosen
}

internal actual fun platformBackend(): LogBackend = JvmBackendRegistry.backend()

internal actual fun installPlatformBackend(backend: LogBackend): Log.Installation = JvmBackendRegistry.install(backend)
