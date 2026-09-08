@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.internal.CallSite
import io.akki.internal.LogRegistry

@CallSite
public val log: Logger
    get() = LogRegistry.forCaller()

@CallSite
public fun logger(): Logger = LogRegistry.forCaller()
