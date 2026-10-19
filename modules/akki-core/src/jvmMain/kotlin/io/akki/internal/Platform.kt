package io.akki.internal

import io.akki.backend.LoggerBinding
import java.util.concurrent.ConcurrentHashMap

internal actual fun printlnToStdErr(message: String): Unit = System.err.println(message)

internal actual val Throwable.isFatal: Boolean get() = this is VirtualMachineError

internal actual val Throwable.isPermanent: Boolean get() = this is LinkageError

internal actual class ThreadSlot<T : Any> actual constructor() {
    private val values = ThreadLocal<T?>()

    actual fun get(): T? = values.get()

    actual fun set(value: T?) {
        if (value == null) values.remove() else values.set(value)
    }
}

internal actual class ScopeBindings actual constructor() {
    private val bindings = ConcurrentHashMap<String, LoggerBinding>()

    actual operator fun get(name: String): LoggerBinding? = bindings[name]

    actual fun putIfAbsent(name: String, binding: LoggerBinding): LoggerBinding? = bindings.putIfAbsent(name, binding)
}
