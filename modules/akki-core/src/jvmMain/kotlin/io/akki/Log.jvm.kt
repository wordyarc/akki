@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.internal.LogRegistry
import io.akki.internal.platformTypeName

public fun Log.of(type: Class<*>): Logger = LogRegistry.of(platformTypeName(type))
