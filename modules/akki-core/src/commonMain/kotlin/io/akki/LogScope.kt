@file:OptIn(ExperimentalContracts::class, ExperimentalAtomicApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.internal.ScopeBindings
import io.akki.internal.exit
import io.akki.internal.scopeEntries
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

    public fun enter(): Entry = Entry(this, scopeEntries.get()).also(scopeEntries::set)

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
            scopeEntries.exit(this, previous, "scope")
            closed = true
        }

        override fun toString(): String = "LogScope.Entry($scope)"
    }

    public companion object {
        @JvmStatic
        public fun currentOrNull(): LogScope? = if (anyScopeCreated.load()) scopeEntries.get()?.scope else null
    }
}
