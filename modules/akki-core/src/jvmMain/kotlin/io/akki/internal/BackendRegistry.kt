package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.backend.LogBackend
import java.io.IOException
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

@OptIn(InternalAkkiApi::class)
internal fun discover(loader: ClassLoader): LogBackend {
    val services = Services(loader)
    val declared = services.load(LogBackend::class.java)
    if (declared.isNotEmpty() || !services.complete) return chooseBackend(declared)
    val factories = services.load(LogBackendFactory::class.java)
    val created = factories.mapNotNull { it.tryCreateBackend() }
    if (created.isEmpty() && factories.isNotEmpty()) {
        return DefaultBackend(factories.joinToString(" ", prefix = "$NO_BACKEND ") { it.hintOnMissing() })
    }
    return chooseBackend(created)
}

internal fun chooseBackend(declared: List<LogBackend>): LogBackend {
    if (declared.isEmpty()) return DefaultBackend("$NO_BACKEND $ADD_BACKEND")
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

internal actual fun discoverPlatformBackend(): LogBackend = discover(LogBackend::class.java.classLoader)

@OptIn(InternalAkkiApi::class)
private fun LogBackendFactory.tryCreateBackend(): LogBackend? =
    try {
        createBackend()
    } catch (failure: Throwable) {
        if (failure.isFatal()) throw failure
        printError("akki: ignoring a failed backend factory ${javaClass.name}: $failure")
        null
    }

private class Services(private val loader: ClassLoader) {
    var complete: Boolean = true
        private set

    fun <S : Any> load(type: Class<S>): List<S> {
        val found = mutableListOf<S>()
        try {
            val providers = ServiceLoader.load(type, loader).iterator()
            while (true) {
                try {
                    if (!providers.hasNext()) return found
                    found += providers.next()
                } catch (failure: Throwable) {
                    if (failure is ServiceConfigurationError && failure.cause is IOException) {
                        printError("akki: backend service enumeration interrupted by an I/O failure: $failure")
                        complete = false
                        return found
                    }
                    if (failure !is ServiceConfigurationError && failure !is LinkageError) throw failure
                    printError("akki: ignoring a broken backend service declaration: $failure")
                }
            }
        } catch (failure: Throwable) {
            if (failure.isFatal()) throw failure
            printError("akki: failed to discover backends\n${failure.stackTraceToString().trimEnd()}")
            complete = false
            return found
        }
    }
}

private const val NO_BACKEND: String = "akki: no backend found on the classpath, writing to stderr at INFO."

private const val ADD_BACKEND: String = "Add a backend such as akki-slf4j to the runtime classpath."
