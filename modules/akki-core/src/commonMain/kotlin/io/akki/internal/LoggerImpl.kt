package io.akki.internal

import io.akki.Level
import io.akki.LogScope
import io.akki.Logger
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class LoggerImpl(override val name: String) : Logger() {
    private val binding = AtomicReference<Binding?>(null)

    private val reported = AtomicReference<LogBackend?>(null)

    override fun sink(level: Level): Sink? {
        val scope = LogScope.current() ?: return resolver().resolve(level)
        return scope.binding(name).resolve(level)
    }

    override fun toString(): String = "Logger($name)"

    fun release(backend: LogBackend) {
        val previous = binding.load()
        if (previous?.backend === backend) binding.compareAndSet(previous, null)
        reported.compareAndSet(backend, null)
    }

    private fun resolver(): LoggerBinding {
        val backend = BackendRegistry.backend()
        val previous = binding.load()
        previous?.takeIf { it.backend === backend }?.let { return it }
        val delegate = try {
            backend.bind(name)
        } catch (failure: Throwable) {
            if (failure.isFatal()) throw failure
            report(backend, failure)
            NO_SINK
        }
        val created = Binding(backend, delegate)
        var expected = previous
        while (!binding.compareAndSet(expected, created)) {
            val current = binding.load()
            if (current?.backend !== backend) return created
            if (!created.isFailed || current.isFailed) return current
            expected = current
        }
        return created
    }

    private fun report(backend: LogBackend, failure: Throwable) {
        val previous = reported.load()
        if (previous === backend || !reported.compareAndSet(previous, backend)) return
        printError(
            "akki: the backend failed to resolve logger '$name', its records are dropped " +
                "until another backend is installed\n" +
                failure.stackTraceToString().trimEnd(),
        )
    }

    private inner class Binding(val backend: LogBackend, private val delegate: LoggerBinding) : LoggerBinding {
        val isFailed: Boolean get() = delegate === NO_SINK

        override fun resolve(level: Level): Sink? =
            try {
                delegate.resolve(level)
            } catch (failure: Throwable) {
                if (failure.isFatal()) throw failure
                binding.compareAndSet(this, Binding(backend, NO_SINK))
                report(backend, failure)
                null
            }
    }

    private companion object {
        val NO_SINK: LoggerBinding = LoggerBinding { null }
    }
}
