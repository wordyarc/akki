@file:OptIn(InternalAkkiApi::class)

package dev.ashenarx.akki

import dev.ashenarx.akki.internal.LogRegistry

public fun Log.of(type: Class<*>): Logger = LogRegistry.of(type.name)
