package io.akki.internal

import io.akki.LogScope

private val scopes = ThreadLocal<LogScope?>()

internal actual fun printError(message: String): Unit = System.err.println(message)

internal actual fun currentScope(): LogScope? = scopes.get()

internal actual fun setCurrentScope(scope: LogScope?) {
    if (scope == null) scopes.remove() else scopes.set(scope)
}
