@file:OptIn(ExperimentalAtomicApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.internal.akkiError
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

public class BackendInstallation internal constructor(
    private val backend: LogBackend,
    private val restore: () -> Boolean,
) : AutoCloseable {
    private val active = AtomicBoolean(true)

    override fun close() {
        if (!active.compareAndSet(expectedValue = true, newValue = false)) return
        if (restore()) return
        active.store(true)
        akkiError("backend installations must be uninstalled in reverse order")
    }

    override fun toString(): String =
        "BackendInstallation(${if (active.load()) "active" else "closed"}, backend=$backend)"
}
