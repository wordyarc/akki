package dev.ashenarx.lokki

import dev.ashenarx.lokki.internal.LogRegistry

public fun Log.of(type: Class<*>): Logger = LogRegistry.of(type.name)
