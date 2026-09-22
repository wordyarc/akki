package io.akki.internal

import io.akki.Level
import io.akki.Logger
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class LoggerImpl(override val name: String) : Logger() {
    @Volatile
    private var binding: Binding? = null

    private val reported = AtomicReference<LogBackend?>(null)

    override fun sink(level: Level): Sink? = resolver().resolve(level)

    override fun toString(): String = "Logger($name)"

    private fun resolver(): LoggerBinding {
        val backend = platformBackend()
        binding?.takeIf { it.backend === backend }?.let { return it }
        val delegate = try {
            backend.bind(name)
        } catch (failure: Throwable) {
            report(backend, failure)
            NO_SINK
        }
        return Binding(backend, delegate).also { binding = it }
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
        override fun resolve(level: Level): Sink? =
            try {
                delegate.resolve(level)
            } catch (failure: Throwable) {
                report(backend, failure)
                binding = Binding(backend, NO_SINK)
                null
            }
    }

    private companion object {
        val NO_SINK: LoggerBinding = LoggerBinding { null }
    }
}
