@file:OptIn(InternalAkkiApi::class)

package io.akki.coroutines

import io.akki.InternalAkkiApi
import io.akki.LogScope
import io.akki.internal.detachedEntry
import io.akki.internal.exchangeCurrentEntry
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

public fun LogScope.asContextElement(): CoroutineContext.Element = LogScopeElement(this)

private class LogScopeElement(private val scope: LogScope) :
    ThreadContextElement<LogScope.Entry?>,
    AbstractCoroutineContextElement(LogScopeElement) {
    private val entry = scope.detachedEntry()

    override fun updateThreadContext(context: CoroutineContext): LogScope.Entry? = exchangeCurrentEntry(entry)

    override fun restoreThreadContext(context: CoroutineContext, oldState: LogScope.Entry?) {
        exchangeCurrentEntry(oldState)
    }

    override fun toString(): String = "LogScopeElement($scope)"

    companion object Key : CoroutineContext.Key<LogScopeElement>
}
