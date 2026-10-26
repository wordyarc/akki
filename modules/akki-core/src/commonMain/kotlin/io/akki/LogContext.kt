@file:OptIn(ExperimentalContracts::class, ExperimentalAtomicApi::class)

package io.akki

import io.akki.internal.contextEntries
import io.akki.internal.exit
import io.akki.internal.plusFields
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

private val anyContextEntered = AtomicBoolean(false)

public inline fun <T> withLogContext(context: LogContext, crossinline block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return context.enter().use { block() }
}

public inline fun <T> withLogFields(vararg fields: Pair<String, Any?>, crossinline block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return withLogContext(LogContext.current().plusFields(fields), block)
}

@JvmSynthetic
public fun <T> LogContext.wrap(task: () -> T): () -> T = { withLogContext(this) { task() } }

public class LogContext internal constructor(public val fields: Map<String, Any?>, validated: Boolean) {
    init {
        if (!validated) fields.keys.forEach(::checkLogContextKey)
    }

    public constructor(fields: Map<String, Any?>) : this(buildMap<String, Any?> { putAll(fields) }, false)

    public constructor(vararg fields: Pair<String, Any?>) : this(buildMap<String, Any?> { putAll(fields) }, false)

    public operator fun get(key: String): Any? = fields[key]

    public operator fun contains(key: String): Boolean = key in fields

    public operator fun plus(other: LogContext): LogContext = when {
        other.fields.isEmpty() -> this
        fields.isEmpty() -> other
        else -> LogContext(buildMap { putAll(fields); putAll(other.fields) }, true)
    }

    public operator fun plus(field: Pair<String, Any?>): LogContext {
        checkLogContextKey(field.first)
        return LogContext(buildMap { putAll(fields); put(field.first, field.second) }, true)
    }

    public operator fun minus(key: String): LogContext =
        if (key in fields) LogContext(buildMap { putAll(fields); remove(key) }, true) else this

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

        override fun toString(): String = "LogContext.Entry($context)"
    }

    public companion object {
        @JvmField
        public val EMPTY: LogContext = LogContext(emptyMap(), true)

        @JvmStatic
        public fun current(): LogContext =
            if (anyContextEntered.load()) contextEntries.get()?.context ?: EMPTY else EMPTY
    }
}

internal fun checkLogContextKey(key: String) {
    require(key.isNotBlank()) { "akki: a log context key must not be blank: every backend writes it as a field name" }
}
