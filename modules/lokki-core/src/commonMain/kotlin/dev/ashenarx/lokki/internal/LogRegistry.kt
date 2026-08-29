package dev.ashenarx.lokki.internal

import dev.ashenarx.lokki.Level
import dev.ashenarx.lokki.Log
import dev.ashenarx.lokki.LogBackend
import dev.ashenarx.lokki.Sink

public object LogRegistry {
    public fun of(name: String): Log = platformLog(name)

    internal fun forCaller(): Log = platformCallerLog()
}

internal object NoOpBackend : LogBackend {
    override fun resolve(name: String, level: Level): Sink? = null
}
