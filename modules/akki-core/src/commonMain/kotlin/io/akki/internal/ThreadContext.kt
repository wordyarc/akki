package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.LogContext

@InternalAkkiApi
public fun LogScope.detachedEntry(): LogScope.Entry = LogScope.Entry(this, null)

@InternalAkkiApi
public fun exchangeCurrentScopeEntry(entry: LogScope.Entry?): LogScope.Entry? =
    scopeEntries.get().also { scopeEntries.set(entry) }

@InternalAkkiApi
public fun LogContext.detachedEntry(): LogContext.Entry = LogContext.Entry(this, null)

@InternalAkkiApi
public fun currentContextEntry(): LogContext.Entry? = contextEntries.get()

@InternalAkkiApi
public fun currentContextOrNull(): LogContext? = contextEntries.get()?.context

@InternalAkkiApi
public fun exchangeCurrentContextEntry(entry: LogContext.Entry?): LogContext.Entry? =
    contextEntries.get().also { contextEntries.set(entry) }
