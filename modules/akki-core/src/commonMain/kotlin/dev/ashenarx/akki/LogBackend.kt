package dev.ashenarx.akki

public fun interface LogBackend {
    public fun resolve(name: String, level: Level): Sink?
}

public fun interface Sink {
    public fun emit(level: Level, message: String, fields: Map<String, Any?>)
}
