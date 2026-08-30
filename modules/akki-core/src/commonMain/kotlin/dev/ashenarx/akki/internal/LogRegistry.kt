package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.Logger
import dev.ashenarx.akki.Sink

public object LogRegistry {
    public fun of(name: String): Logger = platformLogger(name)

    internal fun forCaller(): Logger = platformCallerLogger()
}

internal object NoOpBackend : LogBackend {
    override fun resolve(name: String, level: Level): Sink? = null
}
