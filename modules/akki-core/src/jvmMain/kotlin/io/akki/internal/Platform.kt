package io.akki.internal

import io.akki.LogScope
import io.akki.backend.LoggerBinding
import java.util.concurrent.ConcurrentHashMap

private val scopeEntries = ThreadLocal<LogScope.Entry?>()

internal actual fun printlnToStdErr(message: String): Unit = System.err.println(message)

internal actual val Throwable.isFatal: Boolean get() = this is VirtualMachineError

internal actual val Throwable.isPermanent: Boolean get() = this is LinkageError

internal actual fun currentScopeEntry(): LogScope.Entry? = scopeEntries.get()

internal actual fun setCurrentScopeEntry(entry: LogScope.Entry?) {
    if (entry == null) scopeEntries.remove() else scopeEntries.set(entry)
}

internal actual class ScopeBindings actual constructor() {
    private val bindings = ConcurrentHashMap<String, LoggerBinding>()

    actual operator fun get(name: String): LoggerBinding? = bindings[name]

    actual fun putIfAbsent(name: String, binding: LoggerBinding): LoggerBinding? = bindings.putIfAbsent(name, binding)
}
