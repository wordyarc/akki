@file:OptIn(ExperimentalContracts::class)

package io.akki.test

import io.akki.DelicateAkkiApi
import io.akki.Level
import io.akki.Log
import io.akki.backend.LogBackend
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(DelicateAkkiApi::class)
public inline fun <T> withBackend(backend: LogBackend, block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return Log.install(backend).use { block() }
}

public inline fun recordLogs(
    minLevel: Level = Level.TRACE,
    block: () -> Unit,
): List<LogRecord> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val backend = RecordingBackend(minLevel)
    withBackend(backend, block)
    return backend.records
}
