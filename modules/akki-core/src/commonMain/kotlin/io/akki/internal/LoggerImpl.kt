package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.Level
import io.akki.Logger
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(InternalAkkiApi::class, ExperimentalAtomicApi::class)
internal class LoggerImpl(override val name: String) : Logger() {
    @Volatile
    private var binding: Binding? = null

    private val reported = AtomicBoolean(false)

    override fun isEnabled(level: Level): Boolean = resolver().resolve(level) != null

    override fun sink(level: Level): Sink? = resolver().resolve(level)

    override fun emit(
        level: Level,
        message: String,
        cause: Throwable?,
        fields: Map<String, Any?>,
    ) {
        resolver().resolve(level)?.emit(message, cause, fields)
    }

    override fun toString(): String = "Logger($name)"

    private fun resolver(): LoggerBinding {
        val backend = platformBackend()
        binding?.takeIf { it.backend === backend }?.let { return it.binding }
        val bound = guarded<LoggerBinding?>(null) { backend.bind(name) } ?: return NO_SINK
        return GuardedBinding(bound).also { binding = Binding(backend, it) }
    }

    private inline fun <T> guarded(fallback: T, resolve: () -> T): T =
        try {
            resolve()
        } catch (failure: Throwable) {
            report(failure)
            fallback
        }

    private fun report(failure: Throwable) {
        if (!reported.compareAndSet(false, true)) return
        printError(
            "akki: the backend failed to resolve logger '$name', its records are dropped " +
                "and further failures stay silent\n" +
                failure.stackTraceToString().trimEnd(),
        )
    }

    private inner class GuardedBinding(private val delegate: LoggerBinding) : LoggerBinding {
        override fun resolve(level: Level): Sink? = guarded<Sink?>(null) { delegate.resolve(level) }
    }

    private class Binding(val backend: LogBackend, val binding: LoggerBinding)

    private companion object {
        val NO_SINK: LoggerBinding = LoggerBinding { null }
    }
}
