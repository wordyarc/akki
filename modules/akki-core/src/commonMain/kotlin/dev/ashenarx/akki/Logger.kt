package dev.ashenarx.akki

public interface Logger {
    public val name: String

    public fun isEnabled(level: Level): Boolean

    public fun emit(
        level: Level,
        message: String,
        cause: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Unit

    @InternalAkkiApi
    public fun sink(level: Level): Sink? =
        if (isEnabled(level)) {
            Sink { message, cause, fields -> emit(level, message, cause, fields) }
        } else {
            null
        }
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(InternalAkkiApi::class)
public inline fun Logger.trace(
    message: String,
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
): Unit {
    sink(Level.TRACE)?.emit(message, cause, fields)
}

@OptIn(InternalAkkiApi::class)
public inline fun Logger.trace(
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
    message: () -> String,
): Unit {
    sink(Level.TRACE)?.emit(message(), cause, fields)
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(InternalAkkiApi::class)
public inline fun Logger.debug(
    message: String,
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
): Unit {
    sink(Level.DEBUG)?.emit(message, cause, fields)
}

@OptIn(InternalAkkiApi::class)
public inline fun Logger.debug(
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
    message: () -> String,
): Unit {
    sink(Level.DEBUG)?.emit(message(), cause, fields)
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(InternalAkkiApi::class)
public inline fun Logger.info(
    message: String,
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
): Unit {
    sink(Level.INFO)?.emit(message, cause, fields)
}

@OptIn(InternalAkkiApi::class)
public inline fun Logger.info(
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
    message: () -> String,
): Unit {
    sink(Level.INFO)?.emit(message(), cause, fields)
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(InternalAkkiApi::class)
public inline fun Logger.warn(
    message: String,
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
): Unit {
    sink(Level.WARN)?.emit(message, cause, fields)
}

@OptIn(InternalAkkiApi::class)
public inline fun Logger.warn(
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
    message: () -> String,
): Unit {
    sink(Level.WARN)?.emit(message(), cause, fields)
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(InternalAkkiApi::class)
public inline fun Logger.error(
    message: String,
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
): Unit {
    sink(Level.ERROR)?.emit(message, cause, fields)
}

@OptIn(InternalAkkiApi::class)
public inline fun Logger.error(
    cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
    message: () -> String,
): Unit {
    sink(Level.ERROR)?.emit(message(), cause, fields)
}
