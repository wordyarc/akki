package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.Logger

@InternalAkkiApi
public object LogRegistry {
    public fun of(name: String): Logger = platformLogger(name)

    public fun forCaller(): Logger = platformCallerLogger()
}
