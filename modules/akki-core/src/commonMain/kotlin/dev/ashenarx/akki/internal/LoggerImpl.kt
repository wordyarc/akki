package dev.ashenarx.akki.internal

import dev.ashenarx.akki.InternalAkkiApi
import dev.ashenarx.akki.Level
import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.Logger
import dev.ashenarx.akki.Sink
import dev.ashenarx.akki.SinkResolver
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(InternalAkkiApi::class, ExperimentalAtomicApi::class)
internal class LoggerImpl(override val name: String) : Logger() {
    @Volatile
    private var binding: Binding? = null

    private val reported = AtomicBoolean(false)

    override fun isEnabled(level: Level): Boolean = guarded(false) { platformBackend().isEnabled(name, level) }

    override fun sink(level: Level): Sink? = resolver().resolve(level)

    override fun emit(
        level: Level,
        message: String,
        cause: Throwable?,
        fields: Map<String, Any?>,
    ): Unit {
        resolver().resolve(level)?.emit(message, cause, fields)
    }

    override fun toString(): String = "Logger($name)"

    private fun resolver(): SinkResolver {
        val backend = platformBackend()
        binding?.takeIf { it.backend === backend }?.let { return it.resolver }
        val bound = guarded<SinkResolver?>(null) { backend.bind(name) } ?: return NO_SINK
        return GuardedResolver(bound).also { binding = Binding(backend, it) }
    }

    private inline fun <T> guarded(fallback: T, resolve: () -> T): T =
        try {
            resolve()
        } catch (failure: Exception) {
            report(failure)
            fallback
        }

    private fun report(failure: Exception) {
        if (!reported.compareAndSet(false, true)) return
        printError(
            "akki: the backend failed to resolve logger '$name', its records are dropped " +
                "and further failures stay silent\n" +
                failure.stackTraceToString().trimEnd(),
        )
    }

    private inner class GuardedResolver(private val delegate: SinkResolver) : SinkResolver {
        override fun resolve(level: Level): Sink? = guarded<Sink?>(null) { delegate.resolve(level) }
    }

    private class Binding(val backend: LogBackend, val resolver: SinkResolver)

    private companion object {
        val NO_SINK: SinkResolver = SinkResolver { null }
    }
}
