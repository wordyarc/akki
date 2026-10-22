@file:OptIn(ExperimentalContracts::class, InternalAkkiApi::class)

package io.akki.test

import io.akki.InternalAkkiApi
import io.akki.Level
import io.akki.internal.LogScope
import io.akki.internal.withLogScope
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public inline fun recordLogs(
    minLevel: Level = Level.TRACE,
    crossinline block: () -> Unit,
): List<LogRecord> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val backend = RecordingBackend(minLevel)
    withLogScope(LogScope(backend), block)
    return backend.records
}
