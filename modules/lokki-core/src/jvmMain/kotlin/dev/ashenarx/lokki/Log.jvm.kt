package dev.ashenarx.lokki

import dev.ashenarx.lokki.internal.LogRegistry

public fun Log.Companion.of(type: Class<*>): Log = LogRegistry.of(type.name)
