package io.akki.internal

import io.akki.Log
import io.akki.backend.LogBackend
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicReference

private object JvmBackendRegistry {
    private val backend: AtomicReference<BackendState> =
        AtomicReference(BackendState(discover(LogBackend::class.java.classLoader)))

    fun backend(): LogBackend = backend.get().backend

    fun install(backend: LogBackend): Log.Installation {
        val installed = BackendState(backend)
        val previous = this.backend.getAndSet(installed)
        return Log.Installation(backend) { this.backend.compareAndSet(installed, previous) }
    }

    private class BackendState(val backend: LogBackend)
}

internal fun discover(loader: ClassLoader): LogBackend {
    val declared = mutableListOf<LogBackend>()
    try {
        val providers = ServiceLoader.load(LogBackend::class.java, loader).iterator()
        while (providers.hasNext()) {
            try {
                declared += providers.next()
            } catch (error: ServiceConfigurationError) {
                printError("akki: ignoring a broken backend service declaration: ${error.message}")
            }
        }
    } catch (failure: Throwable) {
        printError("akki: failed to discover backends\n${failure.stackTraceToString().trimEnd()}")
    }
    return chooseBackend(declared)
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
