package io.akki

import io.akki.backend.LogBackend
import io.akki.internal.BackendRegistry
import io.akki.internal.akkiError
import io.akki.internal.namedLogger
import io.akki.internal.platformTypeName
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

public actual object Log {
    @JvmStatic
    public actual fun of(type: KClass<*>): Logger = namedLogger(platformTypeName(type))

    public actual inline fun <reified T : Any> of(): Logger = of(T::class)

    @JvmStatic
    public fun of(type: Class<*>): Logger = namedLogger(platformTypeName(type))

    @JvmStatic
    public actual fun named(name: String): Logger = namedLogger(name)

    @JvmStatic
    @DelicateAkkiApi
    public actual fun install(backend: LogBackend): Installation = BackendRegistry.install(backend)

    public actual class Installation internal actual constructor(
        private val backend: LogBackend,
        private val restore: () -> Boolean,
    ) : AutoCloseable {
        private val active = AtomicBoolean(true)

        actual override fun close() {
            if (!active.compareAndSet(true, false)) return
            if (restore()) return
            active.set(true)
            akkiError("backend installations must be uninstalled in reverse order")
        }

        override fun toString(): String =
            "Log.Installation(${if (active.get()) "active" else "closed"}, backend=$backend)"
    }
}
