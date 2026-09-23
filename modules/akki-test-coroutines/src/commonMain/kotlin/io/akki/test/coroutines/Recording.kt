package io.akki.test.coroutines

import io.akki.Level
import io.akki.LogScope
import io.akki.coroutines.asContextElement
import io.akki.test.LogRecord
import io.akki.test.RecordingBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

public suspend fun recordLogs(
    minLevel: Level = Level.TRACE,
    block: suspend CoroutineScope.() -> Unit,
): List<LogRecord> {
    val backend = RecordingBackend(minLevel)
    withContext(LogScope(backend).asContextElement(), block)
    return backend.records
}
