package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.LogScope

@InternalAkkiApi
public fun LogScope.detachedEntry(): LogScope.Entry = LogScope.Entry(this, null)

@InternalAkkiApi
public fun exchangeCurrentScopeEntry(entry: LogScope.Entry?): LogScope.Entry? =
    currentScopeEntry().also { setCurrentScopeEntry(entry) }
