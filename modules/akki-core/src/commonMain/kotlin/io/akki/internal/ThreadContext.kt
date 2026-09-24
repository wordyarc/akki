package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.LogScope

@InternalAkkiApi
public fun LogScope.detachedEntry(): LogScope.Entry = LogScope.Entry(this, null)

@InternalAkkiApi
public fun exchangeCurrentEntry(entry: LogScope.Entry?): LogScope.Entry? = currentEntry().also { setCurrentEntry(entry) }
