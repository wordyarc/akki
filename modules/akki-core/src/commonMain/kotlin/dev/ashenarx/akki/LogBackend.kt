package dev.ashenarx.akki

public fun interface LogBackend {
    public fun resolve(name: String, level: Level): Sink?
}

public fun interface Sink {
    public fun emit(message: String, fields: Map<String, Any?>, cause: Throwable?)
}
