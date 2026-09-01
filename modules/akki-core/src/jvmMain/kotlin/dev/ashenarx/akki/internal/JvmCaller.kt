package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Log
import dev.ashenarx.akki.Logger
import java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE

private object JvmCaller {
    private const val INTERNAL_PACKAGE: String = "dev.ashenarx.akki.internal"
    private const val INTRINSIC_CLASS: String = "dev.ashenarx.akki.IntrinsicKt"

    private val walker: StackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)
    private val loggers: ClassValue<Logger> = object : ClassValue<Logger>() {
        override fun computeValue(type: Class<*>): Logger = platformLogger(platformTypeName(type))
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

internal actual fun platformCallerLogger(): Logger = JvmCaller.logger()
