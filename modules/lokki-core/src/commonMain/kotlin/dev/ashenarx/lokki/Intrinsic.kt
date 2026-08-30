package dev.ashenarx.lokki

import dev.ashenarx.lokki.internal.CallSite
import dev.ashenarx.lokki.internal.LogRegistry

@CallSite
public val log: Logger
    get() = LogRegistry.forCaller()

@CallSite
public fun logger(): Logger = LogRegistry.forCaller()
