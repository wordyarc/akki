package io.akki.internal

import io.akki.backend.LogBackend
import java.io.IOException
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

internal fun discover(loader: ClassLoader): LogBackend {
    val declared = mutableListOf<LogBackend>()
    try {
        val providers = ServiceLoader.load(LogBackend::class.java, loader).iterator()
        while (true) {
            try {
                if (!providers.hasNext()) break
                declared += providers.next()
            } catch (failure: Throwable) {
                if (failure is ServiceConfigurationError && failure.cause is IOException) {
                    printError("akki: backend service enumeration interrupted by an I/O failure: $failure")
                    break
                }
                if (failure !is ServiceConfigurationError && failure !is LinkageError) throw failure
                printError("akki: ignoring a broken backend service declaration: $failure")
            }
        }
    } catch (failure: Throwable) {
        printError("akki: failed to discover backends\n${failure.stackTraceToString().trimEnd()}")
    }
    return chooseBackend(declared)
}

internal fun chooseBackend(declared: List<LogBackend>): LogBackend {
    if (declared.isEmpty()) return DefaultBackend(NO_BACKEND_NOTICE)
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

private const val NO_BACKEND_NOTICE: String =
    "akki: no backend found on the classpath, writing to stderr at INFO. " +
        "Add a backend such as akki-slf4j to the runtime classpath."
