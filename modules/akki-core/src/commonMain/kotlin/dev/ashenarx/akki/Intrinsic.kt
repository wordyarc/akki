package dev.ashenarx.akki

import dev.ashenarx.akki.internal.CallSite
import dev.ashenarx.akki.internal.LogRegistry

@CallSite
public val log: Logger
    get() = LogRegistry.forCaller()

@CallSite
public fun logger(): Logger = LogRegistry.forCaller()
