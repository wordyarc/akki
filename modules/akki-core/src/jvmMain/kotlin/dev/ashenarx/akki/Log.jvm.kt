@file:OptIn(InternalAkkiApi::class)

package dev.ashenarx.akki

import dev.ashenarx.akki.internal.LogRegistry
import dev.ashenarx.akki.internal.platformTypeName

public fun Log.of(type: Class<*>): Logger = LogRegistry.of(platformTypeName(type))
