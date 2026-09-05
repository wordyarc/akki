package dev.ashenarx.akki

public fun interface LogBackend {
    public fun resolve(name: String, level: Level): Sink?

    public fun isEnabled(name: String, level: Level): Boolean = resolve(name, level) != null

    public fun bind(name: String): SinkResolver = SinkResolver { level -> resolve(name, level) }
}

public fun interface SinkResolver {
    public fun resolve(level: Level): Sink?
}

public fun interface Sink {
    public fun emit(message: String, cause: Throwable?, fields: Map<String, Any?>)
}
