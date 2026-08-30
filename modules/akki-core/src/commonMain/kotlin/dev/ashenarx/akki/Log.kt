package dev.ashenarx.akki

import dev.ashenarx.akki.internal.CallSite
import dev.ashenarx.akki.internal.LogRegistry
import dev.ashenarx.akki.internal.installPlatformBackend
import dev.ashenarx.akki.internal.platformTypeName
import kotlin.reflect.KClass

public object Log {
    public fun of(type: KClass<*>): Logger = LogRegistry.of(platformTypeName(type))

    public inline fun <reified T : Any> of(): Logger = of(T::class)

    @CallSite
    public fun forCaller(): Logger = LogRegistry.forCaller()

    public fun named(name: String): Logger = LogRegistry.of(name)

    public fun install(backend: LogBackend): Unit = installPlatformBackend(backend)
}
