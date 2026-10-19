@file:OptIn(InternalAkkiApi::class)

package io.akki.context.propagation

import io.akki.InternalAkkiApi
import io.akki.LogContext
import io.akki.internal.currentContextEntry
import io.akki.internal.currentContextOrNull
import io.micrometer.context.ThreadLocalAccessor

public class LogContextAccessor : ThreadLocalAccessor<LogContext> {
    override fun key(): Any = KEY

    override fun getValue(): LogContext? = currentContextOrNull()

    override fun setValue(value: LogContext) {
        value.enter()
    }

    override fun setValue() {
        LogContext.EMPTY.enter()
    }

    override fun restore(previousValue: LogContext): Unit = restore()

    override fun restore() {
        currentContextEntry()?.close()
    }

    override fun toString(): String = "LogContextAccessor($KEY)"

    public companion object {
        public const val KEY: String = "io.akki.LogContext"
    }
}
