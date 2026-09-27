package io.akki.internal

import io.akki.LogScope
import io.akki.backend.LoggerBinding
import java.util.concurrent.ConcurrentHashMap

private val entries = ThreadLocal<LogScope.Entry?>()

internal actual fun printError(message: String): Unit = System.err.println(message)

internal actual fun Throwable.isFatal(): Boolean = this is VirtualMachineError

internal actual fun Throwable.isPermanent(): Boolean = this is LinkageError

internal actual fun currentEntry(): LogScope.Entry? = entries.get()

internal actual fun setCurrentEntry(entry: LogScope.Entry?) {
    if (entry == null) entries.remove() else entries.set(entry)
}

internal actual class ScopeBindings actual constructor() {
    private val bindings = ConcurrentHashMap<String, LoggerBinding>()

    actual operator fun get(name: String): LoggerBinding? = bindings[name]

    actual fun putIfAbsent(name: String, binding: LoggerBinding): LoggerBinding? = bindings.putIfAbsent(name, binding)
}
