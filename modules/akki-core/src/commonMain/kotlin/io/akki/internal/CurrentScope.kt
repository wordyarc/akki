package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.LogScope

@InternalAkkiApi
public fun exchangeCurrentScope(scope: LogScope?): LogScope? = currentScope().also { setCurrentScope(scope) }
