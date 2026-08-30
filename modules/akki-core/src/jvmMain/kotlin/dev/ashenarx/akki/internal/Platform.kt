@file:OptIn(InternalAkkiApi::class)

package dev.ashenarx.akki.internal

import dev.ashenarx.akki.InternalAkkiApi
import dev.ashenarx.akki.Log
import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.LogName
import dev.ashenarx.akki.Logger
import java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

private object JvmLogRegistry {
    private val loggers: ConcurrentHashMap<String, Logger> = ConcurrentHashMap()
    private val backend: AtomicReference<BackendState> = AtomicReference(BackendState(DefaultBackend))

    fun logger(name: String): Logger = loggers.computeIfAbsent(name, ::LoggerImpl)

    fun backend(): LogBackend = backend.get().backend

    fun install(backend: LogBackend): Log.Installation {
        val installed = BackendState(backend)
        val previous = this.backend.getAndSet(installed)
        val active = AtomicBoolean(true)
        return Log.Installation {
            if (active.compareAndSet(true, false)) {
                if (!this.backend.compareAndSet(installed, previous)) {
                    active.set(true)
                    error("Backend installations must be uninstalled in reverse order")
                }
            }
        }
    }

    private class BackendState(val backend: LogBackend)
}

private object JvmCaller {
    private const val INTERNAL_PACKAGE: String = "dev.ashenarx.akki.internal"
    private const val INTRINSIC_CLASS: String = "dev.ashenarx.akki.IntrinsicKt"

    private val walker: StackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)
    private val loggers: ClassValue<Logger> = object : ClassValue<Logger>() {
        override fun computeValue(type: Class<*>): Logger = LogRegistry.of(platformTypeName(type))
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

internal actual fun installPlatformBackend(backend: LogBackend): Log.Installation = JvmLogRegistry.install(backend)

internal actual fun platformCallerLogger(): Logger = JvmCaller.logger()

internal actual fun platformTypeName(type: KClass<*>): String = platformTypeName(type.java)

internal fun platformTypeName(type: Class<*>): String =
    type.getDeclaredAnnotation(LogName::class.java)?.value ?: type.name

internal actual fun printError(message: String): Unit = System.err.println(message)
