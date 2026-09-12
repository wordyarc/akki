package io.akki

public class AkkiException internal constructor(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
