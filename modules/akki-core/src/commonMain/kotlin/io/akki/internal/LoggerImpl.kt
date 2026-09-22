package io.akki.internal

import io.akki.Level
import io.akki.Logger
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class LoggerImpl(override val name: String) : Logger() {
    @Volatile
    private var binding: Binding? = null

    private val reported = AtomicBoolean(false)

    override fun sink(level: Level): Sink? = resolver().resolve(level)

    override fun toString(): String = "Logger($name)"

    private fun resolver(): LoggerBinding {
        val backend = platformBackend()
        binding?.takeIf { it.backend === backend }?.let { return it.binding }
        val bound = try {
            GuardedBinding(backend, backend.bind(name))
        } catch (failure: Throwable) {
            report(failure)
            NO_SINK
        }
        binding = Binding(backend, bound)
        return bound
    }

    private fun report(failure: Throwable) {
        if (!reported.compareAndSet(false, true)) return
        printError(
            "akki: the backend failed to resolve logger '$name', its records are dropped " +
                "until another backend is installed\n" +
                failure.stackTraceToString().trimEnd(),
        )
    }

    private inner class GuardedBinding(
        private val backend: LogBackend,
        private val delegate: LoggerBinding,
    ) : LoggerBinding {
        override fun resolve(level: Level): Sink? =
            try {
                delegate.resolve(level)
            } catch (failure: Throwable) {
                report(failure)
                binding = Binding(backend, NO_SINK)
                null
            }
    }

    private class Binding(val backend: LogBackend, val binding: LoggerBinding)

    private companion object {
        val NO_SINK: LoggerBinding = LoggerBinding { null }
    }
}
