@file:OptIn(InternalAkkiApi::class)

package io.akki.coroutines

import io.akki.InternalAkkiApi
import io.akki.LogContext
import io.akki.internal.detachedEntry
import io.akki.internal.exchangeCurrentContextEntry
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext

public fun LogContext.asContextElement(): CoroutineContext.Element = LogContextElement(this)

public suspend fun <T> withLogFields(vararg fields: Pair<String, Any?>, block: suspend CoroutineScope.() -> T): T =
    withContext((LogContext.current() + LogContext(*fields)).asContextElement(), block)

private class LogContextElement(private val logContext: LogContext) :
    ThreadContextElement<LogContext.Entry?>,
    AbstractCoroutineContextElement(LogContextElement) {
    private val entry = logContext.detachedEntry()

    override fun updateThreadContext(context: CoroutineContext): LogContext.Entry? = exchangeCurrentContextEntry(entry)

    override fun restoreThreadContext(context: CoroutineContext, oldState: LogContext.Entry?) {
        exchangeCurrentContextEntry(oldState)
    }

    override fun toString(): String = "LogContextElement($logContext)"

    companion object Key : CoroutineContext.Key<LogContextElement>
}
