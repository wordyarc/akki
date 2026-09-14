@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.internal.CallSite
import io.akki.internal.LogRegistry
import io.akki.internal.installPlatformBackend
import io.akki.internal.platformTypeName
import kotlin.reflect.KClass

public actual object Log {
    public actual fun of(type: KClass<*>): Logger = LogRegistry.of(platformTypeName(type))

    public actual inline fun <reified T : Any> of(): Logger = of(T::class)

    @JvmStatic
    public fun of(type: Class<*>): Logger = LogRegistry.of(platformTypeName(type))

    @JvmStatic
    @CallSite
    public actual fun forCaller(): Logger = LogRegistry.forCaller()

    @JvmStatic
    @CallSite
    public actual fun auto(): Logger = LogRegistry.forCaller()

    @JvmStatic
    public actual fun named(name: String): Logger = LogRegistry.of(name)

    @JvmStatic
    @DelicateAkkiApi
    public actual fun install(backend: LogBackend): Installation = installPlatformBackend(backend)

    public actual class Installation internal actual constructor(uninstall: () -> Unit) : AutoCloseable {
        private val uninstall: () -> Unit = uninstall

        actual override fun close() {
            uninstall.invoke()
        }
    }
}
