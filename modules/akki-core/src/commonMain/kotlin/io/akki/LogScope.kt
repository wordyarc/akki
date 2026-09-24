@file:OptIn(ExperimentalContracts::class, ExperimentalAtomicApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.internal.akkiError
import io.akki.internal.currentEntry
import io.akki.internal.setCurrentEntry
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmStatic

private val used = AtomicBoolean(false)

public inline fun <T> withLogScope(scope: LogScope, crossinline block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return scope.enter().use { block() }
}

public class LogScope(public val backend: LogBackend) {
    private val bindings = AtomicReference<Map<String, LoggerBinding>>(emptyMap())

    init {
        if (!used.load()) used.store(true)
    }

    public fun enter(): Entry = Entry(this, currentEntry()).also(::setCurrentEntry)

    internal inline fun <T> run(crossinline block: () -> T): T {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return withLogScope(this, block)
    }

    internal fun binding(name: String): LoggerBinding {
        var bound: LoggerBinding? = null
        while (true) {
            val current = bindings.load()
            current[name]?.let { return it }
            val created = bound ?: backend.bind(name).also { bound = it }
            if (bindings.compareAndSet(current, current + (name to created))) return created
        }
    }

    override fun toString(): String = "LogScope($backend)"

    public class Entry internal constructor(
        internal val scope: LogScope,
        private val previous: Entry?,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) return
            if (currentEntry() !== this) akkiError("logging scopes must be exited on their own thread in reverse order")
            closed = true
            setCurrentEntry(previous)
        }
    }

    public companion object {
        @JvmStatic
        public fun current(): LogScope? = if (used.load()) currentEntry()?.scope else null
    }
}
