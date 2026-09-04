package dev.ashenarx.akki.test

import dev.ashenarx.akki.DelicateAkkiApi
import dev.ashenarx.akki.Level
import dev.ashenarx.akki.Log
import dev.ashenarx.akki.LogBackend

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
