package dev.ashenarx.lokki.internal

import dev.ashenarx.lokki.Level
import dev.ashenarx.lokki.LogBackend
import dev.ashenarx.lokki.Logger
import dev.ashenarx.lokki.Sink

public object LogRegistry {
    public fun of(name: String): Logger = platformLogger(name)

    internal fun forCaller(): Logger = platformCallerLogger()
}

internal object NoOpBackend : LogBackend {
    override fun resolve(name: String, level: Level): Sink? = null
}
