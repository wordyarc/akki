@file:OptIn(ExperimentalContracts::class)

package io.akki.test

import io.akki.Level
import io.akki.LogScope
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public inline fun recordLogs(
    minLevel: Level = Level.TRACE,
    crossinline block: () -> Unit,
): List<LogRecord> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val backend = RecordingBackend(minLevel)
    LogScope(backend).run(block)
    return backend.records
}
