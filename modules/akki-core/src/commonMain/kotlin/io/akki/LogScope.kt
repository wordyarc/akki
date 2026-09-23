@file:OptIn(ExperimentalContracts::class, ExperimentalAtomicApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.internal.akkiError
import io.akki.internal.currentScope
import io.akki.internal.setCurrentScope
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private val entered = AtomicInt(0)

public class LogScope(public val backend: LogBackend) {
    private val bindings = AtomicReference<Map<String, LoggerBinding>>(emptyMap())

    public fun enter(): Entry {
        val entry = Entry(this, currentScope())
        entered.incrementAndFetch()
        setCurrentScope(this)
        return entry
    }

    public inline fun <T> run(block: () -> T): T {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return enter().use { block() }
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
        private val scope: LogScope,
        private val previous: LogScope?,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) return
            if (currentScope() !== scope) akkiError("logging scopes must be exited on their own thread in reverse order")
            closed = true
            setCurrentScope(previous)
            entered.decrementAndFetch()
        }
    }

    public companion object {
        public fun current(): LogScope? = if (entered.load() == 0) null else currentScope()
    }
}
