@file:OptIn(ExperimentalContracts::class, ExperimentalAtomicApi::class)

package io.akki

import io.akki.internal.contextEntries
import io.akki.internal.exit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

private val anyContextEntered = AtomicBoolean(false)

public inline fun <T> withLogContext(context: LogContext, crossinline block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return context.enter().use { block() }
}

public inline fun <T> withLogFields(vararg fields: Pair<String, Any?>, crossinline block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return withLogContext(LogContext.current() + LogContext(*fields), block)
}

public class LogContext(fields: Map<String, Any?>) {
    public val fields: Map<String, Any?> = fields.toMap()

    init {
        require(this.fields.keys.none { it.isBlank() }) {
            "akki: a log context key must not be blank: every backend writes it as a field name"
        }
    }

    public constructor(vararg fields: Pair<String, Any?>) : this(fields.toMap())

    public operator fun get(key: String): Any? = fields[key]

    public operator fun plus(other: LogContext): LogContext = when {
        other.fields.isEmpty() -> this
        fields.isEmpty() -> other
        else -> LogContext(fields + other.fields)
    }

    public operator fun minus(key: String): LogContext = if (key in fields) LogContext(fields - key) else this

    public fun enter(): Entry = Entry(this, contextEntries.get()).also(contextEntries::set)

    override fun equals(other: Any?): Boolean = this === other || (other is LogContext && fields == other.fields)

    override fun hashCode(): Int = fields.hashCode()

    override fun toString(): String =
        fields.entries.joinToString(prefix = "LogContext(", postfix = ")") { (key, value) -> "$key=$value" }

    public class Entry internal constructor(
        internal val context: LogContext,
        private val previous: Entry?,
    ) : AutoCloseable {
        private var closed = false

        init {
            if (!anyContextEntered.load()) anyContextEntered.store(true)
        }

        override fun close() {
            if (closed) return
            contextEntries.exit(this, previous, "context")
            closed = true
        }
    }

    public companion object {
        @JvmField
        public val EMPTY: LogContext = LogContext(emptyMap())

        @JvmStatic
        public fun current(): LogContext =
            if (anyContextEntered.load()) contextEntries.get()?.context ?: EMPTY else EMPTY
    }
}
