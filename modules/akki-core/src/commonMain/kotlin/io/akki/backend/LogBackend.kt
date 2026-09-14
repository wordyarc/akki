package io.akki.backend

import io.akki.Level

public fun interface LogBackend {
    public fun bind(name: String): LoggerBinding
}

public fun LogBackend(resolve: (name: String, level: Level) -> Sink?): LogBackend =
    LogBackend { name -> LoggerBinding { level -> resolve(name, level) } }

public fun interface LoggerBinding {
    public fun resolve(level: Level): Sink?
}

public fun interface Sink {
    public fun emit(message: String, cause: Throwable?, fields: Map<String, Any?>)
}
