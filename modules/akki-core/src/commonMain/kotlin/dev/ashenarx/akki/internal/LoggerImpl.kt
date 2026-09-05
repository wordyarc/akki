package dev.ashenarx.akki.internal

import dev.ashenarx.akki.InternalAkkiApi
import dev.ashenarx.akki.Level
import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.Logger
import dev.ashenarx.akki.Sink
import dev.ashenarx.akki.SinkResolver
import kotlin.concurrent.Volatile

@OptIn(InternalAkkiApi::class)
internal class LoggerImpl(override val name: String) : Logger() {
    @Volatile
    private var binding: Binding? = null

    override fun isEnabled(level: Level): Boolean = platformBackend().isEnabled(name, level)

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
        return backend.bind(name).also { binding = Binding(backend, it) }
    }

    private class Binding(val backend: LogBackend, val resolver: SinkResolver)
}
