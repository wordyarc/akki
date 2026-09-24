@file:OptIn(InternalAkkiApi::class)

package io.akki.coroutines

import io.akki.InternalAkkiApi
import io.akki.LogScope
import io.akki.internal.exchangeCurrentScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

public fun LogScope.asContextElement(): ThreadContextElement<LogScope?> = LogScopeElement(this)

private class LogScopeElement(private val scope: LogScope) :
    ThreadContextElement<LogScope?>,
    AbstractCoroutineContextElement(LogScopeElement) {
    override fun updateThreadContext(context: CoroutineContext): LogScope? = exchangeCurrentScope(scope)

    override fun restoreThreadContext(context: CoroutineContext, oldState: LogScope?) {
        exchangeCurrentScope(oldState)
    }

    override fun toString(): String = "LogScopeElement($scope)"

    companion object Key : CoroutineContext.Key<LogScopeElement>
}
