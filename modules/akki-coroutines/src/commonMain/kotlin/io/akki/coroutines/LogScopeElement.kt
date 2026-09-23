package io.akki.coroutines

import io.akki.LogScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

public fun LogScope.asContextElement(): ThreadContextElement<LogScope.Entry> = LogScopeElement(this)

private class LogScopeElement(private val scope: LogScope) :
    ThreadContextElement<LogScope.Entry>,
    AbstractCoroutineContextElement(LogScopeElement) {
    override fun updateThreadContext(context: CoroutineContext): LogScope.Entry = scope.enter()

    override fun restoreThreadContext(context: CoroutineContext, oldState: LogScope.Entry): Unit = oldState.close()

    override fun toString(): String = "LogScopeElement($scope)"

    companion object Key : CoroutineContext.Key<LogScopeElement>
}
