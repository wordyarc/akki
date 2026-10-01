@file:OptIn(ExperimentalContracts::class, ExperimentalAtomicApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.internal.ScopeBindings
import io.akki.internal.akkiError
import io.akki.internal.currentEntry
import io.akki.internal.setCurrentEntry
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmStatic

private val anyScopeCreated = AtomicBoolean(false)

public inline fun <T> withLogScope(scope: LogScope, crossinline block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return scope.enter().use { block() }
}

public class LogScope(public val backend: LogBackend) {
    private val bindings = ScopeBindings()

    init {
        if (!anyScopeCreated.load()) anyScopeCreated.store(true)
    }

    public fun enter(): Entry = Entry(this, currentEntry()).also(::setCurrentEntry)

    internal inline fun <T> run(crossinline block: () -> T): T {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return withLogScope(this, block)
    }

    internal fun binding(name: String): LoggerBinding =
        bindings[name] ?: backend.bind(name).let { bindings.putIfAbsent(name, it) ?: it }

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
        public fun currentOrNull(): LogScope? = if (anyScopeCreated.load()) currentEntry()?.scope else null
    }
}
