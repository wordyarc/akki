package dev.ashenarx.lokki

import dev.ashenarx.lokki.internal.CallSite
import dev.ashenarx.lokki.internal.LogRegistry

@CallSite
public val log: Log
    get() = LogRegistry.forCaller()
