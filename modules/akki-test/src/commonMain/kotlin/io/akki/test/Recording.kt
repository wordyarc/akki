package io.akki.test

import io.akki.DelicateAkkiApi
import io.akki.Level
import io.akki.Log
import io.akki.LogBackend

@OptIn(DelicateAkkiApi::class)
public inline fun <T> withBackend(backend: LogBackend, block: () -> T): T =
    Log.install(backend).use { block() }

public fun recordLogs(
    enabled: Set<Level> = Level.entries.toSet(),
    block: () -> Unit,
): List<LogRecord> {
    val backend = RecordingBackend(enabled)
    withBackend(backend, block)
    return backend.records
}
