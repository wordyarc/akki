package io.akki.internal

import io.akki.LogContext
import io.akki.LogScope

internal val scopeEntries: ThreadSlot<LogScope.Entry> = ThreadSlot()

internal val contextEntries: ThreadSlot<LogContext.Entry> = ThreadSlot()

internal fun <E : Any> ThreadSlot<E>.exit(entry: E, previous: E?, concept: String) {
    if (get() !== entry) akkiError("a logging $concept must be exited on the thread that entered it, innermost first")
    set(previous)
}
