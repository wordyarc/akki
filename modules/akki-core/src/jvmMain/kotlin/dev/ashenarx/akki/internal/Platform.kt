package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Log
import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.LogName
import dev.ashenarx.akki.Logger
import java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

private object JvmLogRegistry {
    private val loggers: ConcurrentHashMap<String, Logger> = ConcurrentHashMap()
    private val backend: AtomicReference<LogBackend> = AtomicReference(NoOpBackend)

    fun logger(name: String): Logger = loggers.computeIfAbsent(name, ::LoggerImpl)

    fun backend(): LogBackend = backend.get()

    fun install(backend: LogBackend): Unit {
        this.backend.set(backend)
    }
}

private object JvmCaller {
    private const val INTERNAL_PACKAGE: String = "dev.ashenarx.akki.internal"
    private const val INTRINSIC_CLASS: String = "dev.ashenarx.akki.IntrinsicKt"

    private val walker: StackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)
    private val loggers: ClassValue<Logger> = object : ClassValue<Logger>() {
        override fun computeValue(type: Class<*>): Logger {
            val name = type.getDeclaredAnnotation(LogName::class.java)?.value ?: type.name
            return LogRegistry.of(name)
        }
    }

    fun logger(): Logger {
        val caller = walker.walk { frames ->
            frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(::isCaller)
                .findFirst()
                .orElseThrow()
        }
        return loggers.get(caller)
    }

    private fun isCaller(type: Class<*>): Boolean {
        val packageName = type.packageName
        return packageName != INTERNAL_PACKAGE &&
            !packageName.startsWith("$INTERNAL_PACKAGE.") &&
            type != Log::class.java &&
            type.name != INTRINSIC_CLASS
    }
}

internal actual fun platformLogger(name: String): Logger = JvmLogRegistry.logger(name)

internal actual fun platformBackend(): LogBackend = JvmLogRegistry.backend()

internal actual fun installPlatformBackend(backend: LogBackend): Unit = JvmLogRegistry.install(backend)

internal actual fun platformCallerLogger(): Logger = JvmCaller.logger()

internal actual fun platformTypeName(type: KClass<*>): String = type.java.name
