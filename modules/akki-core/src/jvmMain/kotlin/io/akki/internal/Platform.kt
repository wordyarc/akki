package io.akki.internal

import io.akki.LogScope

private val entries = ThreadLocal<LogScope.Entry?>()

internal actual fun printError(message: String): Unit = System.err.println(message)

internal actual fun Throwable.isFatal(): Boolean = this is VirtualMachineError

internal actual fun currentEntry(): LogScope.Entry? = entries.get()

internal actual fun setCurrentEntry(entry: LogScope.Entry?) {
    if (entry == null) entries.remove() else entries.set(entry)
}
