package dev.ashenarx.lokki.internal

import dev.ashenarx.lokki.Log
import dev.ashenarx.lokki.LogBackend
import dev.ashenarx.lokki.LogName
import java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

private object JvmLogRegistry {
    private val logs: ConcurrentHashMap<String, Log> = ConcurrentHashMap()
    private val backend: AtomicReference<LogBackend> = AtomicReference(NoOpBackend)

    fun log(name: String): Log = logs.computeIfAbsent(name, ::Log)

    fun backend(): LogBackend = backend.get()

    fun install(backend: LogBackend): Unit {
        this.backend.set(backend)
    }
}

private object JvmCaller {
    private const val INTERNAL_PACKAGE: String = "dev.ashenarx.lokki.internal"
    private const val INTRINSIC_CLASS: String = "dev.ashenarx.lokki.IntrinsicKt"

    private val walker: StackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)
    private val logs: ClassValue<Log> = object : ClassValue<Log>() {
        override fun computeValue(type: Class<*>): Log {
            val name = type.getDeclaredAnnotation(LogName::class.java)?.value ?: type.name
            return LogRegistry.of(name)
        }
    }

    fun log(): Log {
        val caller = walker.walk { frames ->
            frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(::isCaller)
                .findFirst()
                .orElseThrow()
        }
        return logs.get(caller)
    }

    private fun isCaller(type: Class<*>): Boolean {
        val packageName = type.packageName
        return packageName != INTERNAL_PACKAGE &&
            !packageName.startsWith("$INTERNAL_PACKAGE.") &&
            type.name != INTRINSIC_CLASS
    }
}

internal actual fun platformLog(name: String): Log = JvmLogRegistry.log(name)

internal actual fun platformBackend(): LogBackend = JvmLogRegistry.backend()

internal actual fun installPlatformBackend(backend: LogBackend): Unit = JvmLogRegistry.install(backend)

internal actual fun platformCallerLog(): Log = JvmCaller.log()

internal actual fun platformTypeName(type: KClass<*>): String = type.java.name
