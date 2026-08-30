package dev.ashenarx.lokki

import dev.ashenarx.lokki.internal.CallSite
import dev.ashenarx.lokki.internal.LogRegistry
import dev.ashenarx.lokki.internal.installPlatformBackend
import dev.ashenarx.lokki.internal.platformTypeName
import kotlin.reflect.KClass

public object Log {
    public fun of(type: KClass<*>): Logger = LogRegistry.of(platformTypeName(type))

    public inline fun <reified T : Any> of(): Logger = of(T::class)

    @CallSite
    public fun ofCaller(): Logger = LogRegistry.forCaller()

    public fun named(name: String): Logger = LogRegistry.of(name)

    public fun install(backend: LogBackend): Unit = installPlatformBackend(backend)
}
