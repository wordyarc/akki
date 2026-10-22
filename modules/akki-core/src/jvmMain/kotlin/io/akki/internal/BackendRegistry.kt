@file:OptIn(InternalAkkiApi::class)

package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.backend.LogBackend
import java.io.IOException
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@OptIn(InternalAkkiApi::class)
internal fun discover(loader: ClassLoader, selected: Setting? = null): LogBackend {
    val services = Services(loader)
    val declared = services.load(LogBackend::class.java)
    if (selected != null) select(selected, declared, services)?.let { return it }
    if (declared.isNotEmpty() || !services.isComplete) return chooseBackend(declared)
    val factories = services.load(LogBackendFactory::class.java)
    val created = factories.mapNotNull { it.tryCreateBackend() }
    if (created.isEmpty() && factories.isNotEmpty()) {
        val hints = factories.mapNotNull { it.tryHintOnMissing() }.ifEmpty { listOf(ADD_BACKEND) }
        return DefaultBackend(hints.joinToString(" ", prefix = "$NO_BACKEND "))
    }
    return chooseBackend(created)
}

@OptIn(InternalAkkiApi::class)
private fun select(selected: Setting, declared: List<LogBackend>, services: Services): LogBackend? {
    val name = selected.value
    declared.firstOrNull { it::class.java.name == name }?.let { return it }
    val factories = services.load(LogBackendFactory::class.java)
    val subject = "the backend '$name' set by ${selected.source}"
    val factory = factories.firstOrNull { it.backendClassName == name }
        ?: return unselected("$subject was not found: ${services.candidates(declared, factories)}")
    val backend = try {
        factory.createBackend()
    } catch (failure: Throwable) {
        if (failure.isFatal) throw failure
        return unselected("$subject failed to start", "\n${failure.stackTraceToString().trimEnd()}")
    }
    return backend ?: unselected("$subject is not available", factory.tryHintOnMissing()?.let { " $it" }.orEmpty())
}

private fun unselected(problem: String, details: String = ""): LogBackend? {
    printlnToStdErr("akki: $problem; selecting the backend automatically.$details")
    return null
}

@OptIn(InternalAkkiApi::class)
private fun Services.candidates(declared: List<LogBackend>, factories: List<LogBackendFactory>): String {
    if (!isComplete) return "the backend service lookup stopped early"
    val names = declared.map { it::class.java.name } + factories.map { it.backendClassName }
    return "the classpath declares ${names.ifEmpty { listOf("no backend") }.joinToString()}"
}

internal fun chooseBackend(backends: List<LogBackend>): LogBackend {
    if (backends.isEmpty()) return DefaultBackend("$NO_BACKEND $ADD_BACKEND")
    val ordered = backends.sortedBy { it::class.java.name }
    val chosen = ordered.first()
    if (ordered.size > 1) {
        printlnToStdErr(
            ordered.joinToString(
                prefix = "akki: multiple backends on the classpath, using ${chosen::class.java.name}: ",
            ) { it::class.java.name },
        )
    }
    return chosen
}

private const val DISCOVERY_WAIT_MILLIS: Long = 1_000

private val discoveryLock = ReentrantLock()

@Volatile
private var discovered: LogBackend? = null

@Volatile
private var stalled: Boolean = false

internal actual fun discoverPlatformBackend(): LogBackend? {
    discovered?.let { return it }
    if (discoveryLock.isHeldByCurrentThread || !awaitDiscovery()) return null
    try {
        return discovered
            ?: discover(LogBackend::class.java.classLoader, AkkiProperties[BACKEND_PROPERTY]).also { discovered = it }
    } finally {
        discoveryLock.unlock()
    }
}

private fun awaitDiscovery(): Boolean {
    if (discoveryLock.tryLock()) return true
    if (stalled) return false
    try {
        if (discoveryLock.tryLock(DISCOVERY_WAIT_MILLIS, TimeUnit.MILLISECONDS)) return true
        stalled = true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
    return false
}

@OptIn(InternalAkkiApi::class)
private fun LogBackendFactory.tryCreateBackend(): LogBackend? = isolated { createBackend() }

@OptIn(InternalAkkiApi::class)
private fun LogBackendFactory.tryHintOnMissing(): String? = isolated { hintOnMissing() }

@OptIn(InternalAkkiApi::class)
private inline fun <T : Any> LogBackendFactory.isolated(call: LogBackendFactory.() -> T?): T? =
    try {
        call()
    } catch (failure: Throwable) {
        if (failure.isFatal) throw failure
        printlnToStdErr("akki: backend factory ${javaClass.name} failed and is skipped: $failure")
        null
    }

private class Services(private val loader: ClassLoader) {
    var isComplete: Boolean = true
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
                    val cause = (failure as? ServiceConfigurationError)?.cause
                    if (cause != null && cause.isFatal) throw cause
                    if (cause is IOException) {
                        printlnToStdErr(
                            "akki: an I/O error stopped the backend service lookup: $failure",
                        )
                        isComplete = false
                        return found
                    }
                    if (failure !is ServiceConfigurationError && failure !is LinkageError) throw failure
                    printlnToStdErr(
                        "akki: skipping an invalid backend service declaration\n${failure.stackTraceToString().trimEnd()}",
                    )
                }
            }
        } catch (failure: Throwable) {
            if (failure.isFatal) throw failure
            printlnToStdErr("akki: backend discovery failed\n${failure.stackTraceToString().trimEnd()}")
            isComplete = false
            return found
        }
    }
}

private const val NO_BACKEND: String = "akki: no backend is available, writing INFO and higher to stderr."

private const val ADD_BACKEND: String = "Add a backend such as akki-slf4j to the runtime classpath."
