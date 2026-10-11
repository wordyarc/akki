package io.akki

import io.akki.backend.LogBackend
import io.akki.internal.BackendRegistry
import io.akki.internal.getLogger
import io.akki.internal.loggerName
import kotlin.reflect.KClass

public actual object Log {
    @JvmStatic
    public actual fun of(kClass: KClass<*>): Logger = getLogger(loggerName(kClass))

    public actual inline fun <reified T : Any> of(): Logger = of(T::class)

    @JvmStatic
    public fun of(jClass: Class<*>): Logger = getLogger(loggerName(jClass))

    @JvmStatic
    public actual fun named(name: String): Logger = getLogger(name)

    @JvmStatic
    @DelicateAkkiApi
    public actual fun install(backend: LogBackend): BackendInstallation = BackendRegistry.install(backend)
}
