@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.internal.CallSite
import io.akki.internal.LogRegistry
import io.akki.internal.installPlatformBackend
import io.akki.internal.platformTypeName
import kotlin.reflect.KClass

public object Log {
    public class Installation internal constructor(
        private val uninstall: () -> Unit,
    ) : AutoCloseable {
        public fun uninstall(): Unit = uninstall.invoke()

        override fun close(): Unit = uninstall()
    }

    public fun of(type: KClass<*>): Logger = LogRegistry.of(platformTypeName(type))

    public inline fun <reified T : Any> of(): Logger = of(T::class)

    @CallSite
    public fun forCaller(): Logger = LogRegistry.forCaller()

    @CallSite
    public fun auto(): Logger = LogRegistry.forCaller()

    public fun named(name: String): Logger = LogRegistry.of(name)

    @DelicateAkkiApi
    public fun install(backend: LogBackend): Installation = installPlatformBackend(backend)
}
