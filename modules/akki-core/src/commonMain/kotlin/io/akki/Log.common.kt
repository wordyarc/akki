@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.backend.LogBackend
import io.akki.internal.CallSite
import kotlin.reflect.KClass

public expect object Log {
    public fun of(type: KClass<*>): Logger

    public inline fun <reified T : Any> of(): Logger

    @CallSite
    public fun forCaller(): Logger

    @CallSite
    public fun auto(): Logger

    public fun named(name: String): Logger

    @DelicateAkkiApi
    public fun install(backend: LogBackend): Installation

    public class Installation internal constructor(uninstall: () -> Unit) : AutoCloseable {
        override fun close()
    }
}
