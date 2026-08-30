package dev.ashenarx.akki.internal

import dev.ashenarx.akki.InternalAkkiApi
import dev.ashenarx.akki.Logger

@InternalAkkiApi
public object LogRegistry {
    public fun of(name: String): Logger = platformLogger(name)

    public fun forCaller(): Logger = platformCallerLogger()
}
